package com.zwskchat.zwskchat.contract.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizContractMilestone;

import java.util.List;

public interface ContractService {
    IPage<BizContract> list(Page<BizContract> page, String status, String keyword);
    BizContract getById(Long id);
    BizContract create(BizContract contract);
    BizContract update(Long id, BizContract contract);
    void delete(Long id);

    List<BizContractMilestone> listMilestones(Long contractId);
    BizContractMilestone createMilestone(BizContractMilestone milestone);
    void updateMilestoneStatus(Long milestoneId, String status);
}
