package com.zwskchat.zwskchat.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizContractMilestone;
import com.zwskchat.zwskchat.contract.mapper.BizContractMapper;
import com.zwskchat.zwskchat.contract.mapper.BizContractMilestoneMapper;
import com.zwskchat.zwskchat.contract.service.ContractService;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private static final long DEFAULT_PAGE_CURRENT = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;
    private static final String DEFAULT_CONTRACT_STATUS = "draft";
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String DEFAULT_MILESTONE_STATUS = "pending";

    private final BizContractMapper contractMapper;
    private final BizContractMilestoneMapper milestoneMapper;
    private final BizCustomerMapper customerMapper;

    @Override
    public IPage<BizContract> list(Page<BizContract> page, String status, String keyword) {
        normalizePage(page);
        LambdaQueryWrapper<BizContract> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(BizContract::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper.like(BizContract::getTitle, keyword)
                    .or().like(BizContract::getContractNo, keyword));
        }
        query.orderByDesc(BizContract::getCreateTime);
        return contractMapper.selectPage(page, query);
    }

    @Override
    public BizContract getById(Long id) {
        BizContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "contract not found");
        }
        return contract;
    }

    @Override
    @Transactional
    public BizContract create(BizContract contract) {
        LoginUser user = SecurityUtils.currentUser();
        assertCustomerBelongsToTenant(contract.getCustomerId(), user.getTenantId());
        contract.setTenantId(user.getTenantId());
        contract.setCreateBy(user.getUserId());
        if (contract.getOwnerId() == null) {
            contract.setOwnerId(user.getUserId());
        }
        if (contract.getAmount() == null) {
            contract.setAmount(BigDecimal.ZERO);
        }
        if (contract.getCurrency() == null || contract.getCurrency().isBlank()) {
            contract.setCurrency(DEFAULT_CURRENCY);
        }
        if (contract.getStatus() == null || contract.getStatus().isBlank()) {
            contract.setStatus(DEFAULT_CONTRACT_STATUS);
        }
        contractMapper.insert(contract);
        return contract;
    }

    @Override
    @Transactional
    public BizContract update(Long id, BizContract contract) {
        BizContract existing = getById(id);
        assertCustomerBelongsToTenant(contract.getCustomerId(), existing.getTenantId());
        contract.setId(id);
        contract.setTenantId(existing.getTenantId());
        contract.setCreateBy(existing.getCreateBy());
        if (contract.getAmount() == null) {
            contract.setAmount(existing.getAmount());
        }
        if (contract.getCurrency() == null || contract.getCurrency().isBlank()) {
            contract.setCurrency(existing.getCurrency());
        }
        if (contract.getStatus() == null || contract.getStatus().isBlank()) {
            contract.setStatus(existing.getStatus());
        }
        contractMapper.updateById(contract);
        return contractMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        contractMapper.deleteById(id);
    }

    @Override
    public List<BizContractMilestone> listMilestones(Long contractId) {
        getById(contractId);
        return milestoneMapper.selectList(new LambdaQueryWrapper<BizContractMilestone>()
                .eq(BizContractMilestone::getContractId, contractId)
                .orderByAsc(BizContractMilestone::getDueDate));
    }

    @Override
    @Transactional
    public BizContractMilestone createMilestone(BizContractMilestone milestone) {
        BizContract contract = getById(milestone.getContractId());
        milestone.setTenantId(contract.getTenantId());
        if (milestone.getStatus() == null || milestone.getStatus().isBlank()) {
            milestone.setStatus(DEFAULT_MILESTONE_STATUS);
        }
        milestoneMapper.insert(milestone);
        return milestone;
    }

    @Override
    @Transactional
    public void updateMilestoneStatus(Long milestoneId, String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "milestone status is required");
        }
        BizContractMilestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "milestone not found");
        }
        getById(milestone.getContractId());
        milestone.setStatus(status);
        milestoneMapper.updateById(milestone);
    }

    private void assertCustomerBelongsToTenant(Long customerId, Long tenantId) {
        if (customerId == null) {
            return;
        }
        Long count = customerMapper.selectCount(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getId, customerId)
                .eq(BizCustomer::getTenantId, tenantId));
        if (count == null || count == 0L) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "customer is invalid for this tenant");
        }
    }

    private void normalizePage(Page<?> page) {
        if (page.getCurrent() < DEFAULT_PAGE_CURRENT) {
            page.setCurrent(DEFAULT_PAGE_CURRENT);
        }
        if (page.getSize() <= 0) {
            page.setSize(DEFAULT_PAGE_SIZE);
        }
        if (page.getSize() > MAX_PAGE_SIZE) {
            page.setSize(MAX_PAGE_SIZE);
        }
    }
}
