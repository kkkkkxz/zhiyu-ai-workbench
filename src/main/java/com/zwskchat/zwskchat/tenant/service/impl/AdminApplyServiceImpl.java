package com.zwskchat.zwskchat.tenant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.PageResult;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyRequest;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyResponse;
import com.zwskchat.zwskchat.tenant.entity.AdminApply;
import com.zwskchat.zwskchat.tenant.mapper.AdminApplyMapper;
import com.zwskchat.zwskchat.tenant.service.AdminApplyService;
import com.zwskchat.zwskchat.user.entity.SysRole;
import com.zwskchat.zwskchat.user.entity.SysUserRole;
import com.zwskchat.zwskchat.user.mapper.SysRoleMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminApplyServiceImpl implements AdminApplyService {

    private static final Set<String> APPROVABLE_ROLES = Set.of("SUPER_ADMIN", "TENANT_ADMIN", "KB_ADMIN");

    private final AdminApplyMapper applyMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    @Transactional
    public AdminApplyResponse submit(AdminApplyRequest request) {
        if (!APPROVABLE_ROLES.contains(request.getTargetRole())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许申请该角色");
        }
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        AdminApply apply = new AdminApply();
        apply.setTenantId(tenantId);
        apply.setUserId(request.getUserId());
        apply.setApplyType("ROLE");
        apply.setTargetRole(request.getTargetRole());
        apply.setReason(request.getReason());
        apply.setStatus("PENDING");
        applyMapper.insert(apply);
        return AdminApplyResponse.from(apply);
    }

    @Override
    public PageResult<AdminApplyResponse> listPending(long page, long size) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            Page<AdminApply> p = applyMapper.selectPage(
                    new Page<>(page, size),
                    Wrappers.<AdminApply>lambdaQuery()
                            .eq(AdminApply::getStatus, "PENDING")
                            .eq(AdminApply::getDeleted, 0)
                            .orderByDesc(AdminApply::getCreateTime));
            List<AdminApplyResponse> records = p.getRecords().stream()
                    .map(AdminApplyResponse::from)
                    .toList();
            return PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), records);
        }
    }

    @Override
    @Transactional
    public AdminApplyResponse approve(Long applyId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            AdminApply apply = applyMapper.selectById(applyId);
            if (apply == null) throw new BusinessException(ResultCode.NOT_FOUND, "申请不存在");
            if (!"PENDING".equals(apply.getStatus())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "该申请已处理");
            }
            apply.setStatus("APPROVED");
            apply.setReviewerId(SecurityUtils.currentUserId());
            apply.setReviewTime(LocalDateTime.now());
            applyMapper.updateById(apply);

            // 给用户赋角色
            SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                    .eq(SysRole::getTenantId, apply.getTenantId())
                    .eq(SysRole::getRoleCode, apply.getTargetRole())
                    .eq(SysRole::getDeleted, 0)
                    .last("LIMIT 1"));
            if (role != null) {
                long exists = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, apply.getUserId())
                        .eq(SysUserRole::getRoleId, role.getId()));
                if (exists == 0) {
                    SysUserRole ur = new SysUserRole();
                    ur.setTenantId(apply.getTenantId());
                    ur.setUserId(apply.getUserId());
                    ur.setRoleId(role.getId());
                    userRoleMapper.insert(ur);
                }
            }
            return AdminApplyResponse.from(apply);
        }
    }

    @Override
    @Transactional
    public AdminApplyResponse reject(Long applyId, String reason) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            AdminApply apply = applyMapper.selectById(applyId);
            if (apply == null) throw new BusinessException(ResultCode.NOT_FOUND, "申请不存在");
            if (!"PENDING".equals(apply.getStatus())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "该申请已处理");
            }
            apply.setStatus("REJECTED");
            apply.setReviewerId(SecurityUtils.currentUserId());
            apply.setReviewTime(LocalDateTime.now());
            apply.setRejectReason(reason);
            applyMapper.updateById(apply);
            return AdminApplyResponse.from(apply);
        }
    }
}
