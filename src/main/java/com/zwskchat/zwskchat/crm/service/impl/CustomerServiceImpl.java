package com.zwskchat.zwskchat.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.crm.entity.BizContact;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizFollowUp;
import com.zwskchat.zwskchat.crm.mapper.BizContactMapper;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.crm.mapper.BizFollowUpMapper;
import com.zwskchat.zwskchat.crm.service.CustomerService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final long DEFAULT_PAGE_CURRENT = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;
    private static final String DEFAULT_CUSTOMER_TYPE = "enterprise";
    private static final String DEFAULT_CUSTOMER_STATUS = "potential";
    private static final int PRIMARY_CONTACT = 1;
    private static final int NORMAL_CONTACT = 0;

    private final BizCustomerMapper customerMapper;
    private final BizContactMapper contactMapper;
    private final BizFollowUpMapper followUpMapper;

    @Override
    public IPage<BizCustomer> listCustomers(Page<BizCustomer> page, String status, String keyword) {
        normalizePage(page);
        LambdaQueryWrapper<BizCustomer> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(BizCustomer::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper.like(BizCustomer::getName, keyword)
                    .or().like(BizCustomer::getShortName, keyword)
                    .or().like(BizCustomer::getPhone, keyword));
        }
        query.orderByDesc(BizCustomer::getCreateTime);
        return customerMapper.selectPage(page, query);
    }

    @Override
    public BizCustomer getById(Long id) {
        BizCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "customer not found");
        }
        return customer;
    }

    @Override
    @Transactional
    public BizCustomer create(BizCustomer customer) {
        LoginUser user = SecurityUtils.currentUser();
        customer.setTenantId(user.getTenantId());
        customer.setCreateBy(user.getUserId());
        if (customer.getOwnerId() == null) {
            customer.setOwnerId(user.getUserId());
        }
        if (customer.getType() == null || customer.getType().isBlank()) {
            customer.setType(DEFAULT_CUSTOMER_TYPE);
        }
        if (customer.getStatus() == null || customer.getStatus().isBlank()) {
            customer.setStatus(DEFAULT_CUSTOMER_STATUS);
        }
        customerMapper.insert(customer);
        return customer;
    }

    @Override
    @Transactional
    public BizCustomer update(Long id, BizCustomer customer) {
        BizCustomer existing = getById(id);
        customer.setId(existing.getId());
        customer.setTenantId(existing.getTenantId());
        customer.setCreateBy(existing.getCreateBy());
        if (customer.getType() == null || customer.getType().isBlank()) {
            customer.setType(existing.getType());
        }
        if (customer.getStatus() == null || customer.getStatus().isBlank()) {
            customer.setStatus(existing.getStatus());
        }
        customerMapper.updateById(customer);
        return customerMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        customerMapper.deleteById(id);
    }

    @Override
    public List<BizContact> listContacts(Long customerId) {
        getById(customerId);
        return contactMapper.selectList(new LambdaQueryWrapper<BizContact>()
                .eq(BizContact::getCustomerId, customerId));
    }

    @Override
    @Transactional
    public BizContact createContact(BizContact contact) {
        BizCustomer customer = getById(contact.getCustomerId());
        contact.setTenantId(customer.getTenantId());
        contact.setIsPrimary(normalizePrimary(contact.getIsPrimary()));
        contactMapper.insert(contact);
        return contact;
    }

    @Override
    @Transactional
    public void deleteContact(Long contactId) {
        BizContact contact = contactMapper.selectById(contactId);
        if (contact == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "contact not found");
        }
        contactMapper.deleteById(contactId);
    }

    @Override
    public IPage<BizFollowUp> listFollowUps(Long customerId, Page<BizFollowUp> page) {
        getById(customerId);
        normalizePage(page);
        return followUpMapper.selectPage(page, new LambdaQueryWrapper<BizFollowUp>()
                .eq(BizFollowUp::getCustomerId, customerId)
                .orderByDesc(BizFollowUp::getCreateTime));
    }

    @Override
    @Transactional
    public BizFollowUp createFollowUp(BizFollowUp followUp) {
        BizCustomer customer = getById(followUp.getCustomerId());
        followUp.setTenantId(customer.getTenantId());
        followUp.setUserId(SecurityUtils.currentUser().getUserId());
        followUpMapper.insert(followUp);
        return followUp;
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

    private Integer normalizePrimary(Integer value) {
        return Integer.valueOf(PRIMARY_CONTACT).equals(value) ? PRIMARY_CONTACT : NORMAL_CONTACT;
    }
}
