package com.zwskchat.zwskchat.attachment.service.impl;

import com.zwskchat.zwskchat.attachment.service.AttachmentPermissionService;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.contract.mapper.BizContractMapper;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.dispatch.mapper.BizDispatchMapper;
import com.zwskchat.zwskchat.schedule.mapper.BizScheduleMapper;
import com.zwskchat.zwskchat.workflow.mapper.WfInstanceMapper;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultAttachmentPermissionService implements AttachmentPermissionService {

    private static final String CONTRACT = "contract";
    private static final String WORK_ORDER = "work_order";
    private static final String CUSTOMER = "customer";
    private static final String DISPATCH = "dispatch";
    private static final String SCHEDULE = "schedule";
    private static final String WORKFLOW = "workflow";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            CONTRACT, WORK_ORDER, CUSTOMER, DISPATCH, SCHEDULE, WORKFLOW);

    private final BizContractMapper contractMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizCustomerMapper customerMapper;
    private final BizDispatchMapper dispatchMapper;
    private final BizScheduleMapper scheduleMapper;
    private final WfInstanceMapper workflowInstanceMapper;

    @Override
    public void assertReadable(String bizType, Long bizId) {
        assertExists(bizType, bizId);
    }

    @Override
    public void assertWritable(String bizType, Long bizId) {
        assertExists(bizType, bizId);
    }

    private void assertExists(String bizType, Long bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "业务对象参数不完整");
        }
        if (!SUPPORTED_TYPES.contains(bizType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的附件业务类型: " + bizType);
        }
        boolean exists = switch (bizType) {
            case CONTRACT -> contractMapper.selectById(bizId) != null;
            case WORK_ORDER -> workOrderMapper.selectById(bizId) != null;
            case CUSTOMER -> customerMapper.selectById(bizId) != null;
            case DISPATCH -> dispatchMapper.selectById(bizId) != null;
            case SCHEDULE -> scheduleMapper.selectById(bizId) != null;
            case WORKFLOW -> workflowInstanceMapper.selectById(bizId) != null;
            default -> false;
        };
        if (!exists) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关联业务记录不存在");
        }
    }
}
