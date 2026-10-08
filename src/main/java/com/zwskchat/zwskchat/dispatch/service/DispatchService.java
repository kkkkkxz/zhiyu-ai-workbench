package com.zwskchat.zwskchat.dispatch.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatchAssignee;
import com.zwskchat.zwskchat.dispatch.dto.DispatchDeptOption;
import com.zwskchat.zwskchat.dispatch.dto.DispatchUserOption;

import java.util.List;

public interface DispatchService {
    IPage<BizDispatch> mySent(Page<BizDispatch> page);
    IPage<BizDispatch> myReceived(Page<BizDispatch> page);
    BizDispatch getById(Long id);
    BizDispatch create(BizDispatch dispatch, List<Long> assigneeIds);
    void updateProgress(Long dispatchId, Long assigneeId, int progress, String note);
    void complete(Long dispatchId, String completionNote);
    List<BizDispatchAssignee> listAssignees(Long dispatchId);
    List<DispatchDeptOption> assignableDepartments();
    List<DispatchUserOption> assignableUsers(Long deptId);
}
