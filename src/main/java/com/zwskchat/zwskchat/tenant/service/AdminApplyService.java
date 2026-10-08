package com.zwskchat.zwskchat.tenant.service;

import com.zwskchat.zwskchat.common.api.PageResult;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyRequest;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyResponse;

public interface AdminApplyService {

    AdminApplyResponse submit(AdminApplyRequest request);

    PageResult<AdminApplyResponse> listPending(long page, long size);

    AdminApplyResponse approve(Long applyId);

    AdminApplyResponse reject(Long applyId, String reason);
}
