package com.zwskchat.zwskchat.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.dispatch.dto.DispatchDeptOption;
import com.zwskchat.zwskchat.dispatch.dto.DispatchUserOption;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatchAssignee;
import com.zwskchat.zwskchat.dispatch.mapper.BizDispatchAssigneeMapper;
import com.zwskchat.zwskchat.dispatch.mapper.BizDispatchMapper;
import com.zwskchat.zwskchat.dispatch.service.DispatchService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.user.entity.SysDept;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.mapper.SysDeptMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final BizDispatchMapper dispatchMapper;
    private final BizDispatchAssigneeMapper assigneeMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    @Override
    public IPage<BizDispatch> mySent(Page<BizDispatch> page) {
        Long userId = SecurityUtils.currentUser().getUserId();
        return dispatchMapper.selectPage(page,
                new LambdaQueryWrapper<BizDispatch>()
                        .eq(BizDispatch::getDispatcherId, userId)
                        .orderByDesc(BizDispatch::getCreateTime));
    }

    @Override
    public IPage<BizDispatch> myReceived(Page<BizDispatch> page) {
        Long userId = SecurityUtils.currentUser().getUserId();
        List<BizDispatchAssignee> assignments = assigneeMapper.selectList(
                new LambdaQueryWrapper<BizDispatchAssignee>().eq(BizDispatchAssignee::getUserId, userId));
        if (assignments.isEmpty()) return new Page<>();
        List<Long> dispatchIds = assignments.stream().map(BizDispatchAssignee::getDispatchId).toList();
        return dispatchMapper.selectPage(page,
                new LambdaQueryWrapper<BizDispatch>()
                        .in(BizDispatch::getId, dispatchIds)
                        .orderByDesc(BizDispatch::getCreateTime));
    }

    @Override
    public BizDispatch getById(Long id) {
        BizDispatch d = dispatchMapper.selectById(id);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND, "派发记录不存在");
        return d;
    }

    @Override
    @Transactional
    public BizDispatch create(BizDispatch dispatch, List<Long> assigneeIds) {
        dispatch.setDispatcherId(SecurityUtils.currentUser().getUserId());
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择指派人员");
        }
        List<Long> allowedUserIds = assignableUsers(null).stream().map(DispatchUserOption::getId).toList();
        for (Long uid : assigneeIds) {
            if (!allowedUserIds.contains(uid)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权指派该人员");
            }
        }
        dispatchMapper.insert(dispatch);
        for (Long uid : assigneeIds) {
            BizDispatchAssignee a = new BizDispatchAssignee();
            a.setDispatchId(dispatch.getId());
            a.setUserId(uid);
            a.setStatus("pending");
            a.setProgress(0);
            assigneeMapper.insert(a);
        }
        return dispatch;
    }

    @Override
    @Transactional
    public void updateProgress(Long dispatchId, Long assigneeId, int progress, String note) {
        Long currentUserId = SecurityUtils.currentUser().getUserId();
        if (!currentUserId.equals(assigneeId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能提交自己的任务结果");
        }
        BizDispatchAssignee a = assigneeMapper.selectOne(
                new LambdaQueryWrapper<BizDispatchAssignee>()
                        .eq(BizDispatchAssignee::getDispatchId, dispatchId)
                        .eq(BizDispatchAssignee::getUserId, assigneeId));
        if (a == null) throw new BusinessException(ResultCode.NOT_FOUND, "指派记录不存在");
        a.setProgress(progress);
        a.setNote(note);
        if (progress >= 100) {
            a.setStatus("completed");
            a.setCompletedAt(LocalDateTime.now());
        } else {
            a.setStatus("in_progress");
        }
        assigneeMapper.updateById(a);
        syncDispatchStatus(dispatchId, progress >= 100 ? note : null);
    }

    @Override
    @Transactional
    public void complete(Long dispatchId, String completionNote) {
        updateProgress(dispatchId, SecurityUtils.currentUser().getUserId(), 100, completionNote);
    }

    @Override
    public List<BizDispatchAssignee> listAssignees(Long dispatchId) {
        return assigneeMapper.selectList(
                new LambdaQueryWrapper<BizDispatchAssignee>()
                        .eq(BizDispatchAssignee::getDispatchId, dispatchId));
    }

    @Override
    public List<DispatchDeptOption> assignableDepartments() {
        LoginUser current = SecurityUtils.currentUser();
        Long tenantId = current.getTenantId();
        List<SysDept> rows;
        if (isSuperAdmin(current)) {
            rows = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getTenantId, tenantId)
                    .eq(SysDept::getStatus, 1)
                    .orderByAsc(SysDept::getSort)
                    .orderByAsc(SysDept::getId));
        } else {
            rows = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getTenantId, tenantId)
                    .eq(SysDept::getLeaderUserId, current.getUserId())
                    .eq(SysDept::getStatus, 1)
                    .orderByAsc(SysDept::getSort)
                    .orderByAsc(SysDept::getId));
        }
        return rows.stream()
                .map(d -> DispatchDeptOption.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .build())
                .toList();
    }

    @Override
    public List<DispatchUserOption> assignableUsers(Long deptId) {
        LoginUser current = SecurityUtils.currentUser();
        Long tenantId = current.getTenantId();
        List<Long> deptIds = assignableDepartments().stream().map(DispatchDeptOption::getId).toList();
        if (deptIds.isEmpty()) {
            return List.of();
        }
        if (deptId != null && !deptIds.contains(deptId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该部门人员");
        }
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getStatus, 1)
                .in(SysUser::getDeptId, deptId == null ? deptIds : List.of(deptId))
                .orderByAsc(SysUser::getDeptId)
                .orderByAsc(SysUser::getId);
        return userMapper.selectList(query).stream()
                .map(u -> DispatchUserOption.builder()
                        .id(u.getId())
                        .deptId(u.getDeptId())
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .build())
                .toList();
    }

    private boolean isSuperAdmin(LoginUser user) {
        return user.getRoles() != null && user.getRoles().contains("SUPER_ADMIN");
    }

    private void syncDispatchStatus(Long dispatchId, String completionNote) {
        BizDispatch dispatch = dispatchMapper.selectById(dispatchId);
        if (dispatch == null) {
            return;
        }
        List<BizDispatchAssignee> assignees = assigneeMapper.selectList(
                new LambdaQueryWrapper<BizDispatchAssignee>()
                        .eq(BizDispatchAssignee::getDispatchId, dispatchId));
        if (assignees.isEmpty()) {
            return;
        }
        boolean allCompleted = assignees.stream().allMatch(a -> "completed".equals(a.getStatus()));
        boolean anyStarted = assignees.stream().anyMatch(a ->
                "in_progress".equals(a.getStatus()) || "completed".equals(a.getStatus()));
        if (allCompleted) {
            dispatch.setStatus("completed");
            dispatch.setCompletedAt(LocalDateTime.now());
            dispatch.setCompletionNote(completionNote);
        } else if (anyStarted) {
            dispatch.setStatus("in_progress");
        }
        dispatchMapper.updateById(dispatch);
    }
}
