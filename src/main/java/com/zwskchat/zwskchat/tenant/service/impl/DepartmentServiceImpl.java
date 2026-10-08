package com.zwskchat.zwskchat.tenant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.tenant.dto.*;
import com.zwskchat.zwskchat.tenant.service.DepartmentService;
import com.zwskchat.zwskchat.user.entity.SysDept;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.entity.SysUserReporting;
import com.zwskchat.zwskchat.user.mapper.SysDeptMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserReportingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysUserReportingMapper reportingMapper;

    @Override
    public List<DepartmentResponse> tree(Long tenantId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysDept> depts = deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                    .eq(SysDept::getTenantId, tenantId)
                    .eq(SysDept::getDeleted, 0)
                    .orderByAsc(SysDept::getSort)
                    .orderByAsc(SysDept::getId));
            return buildTree(depts);
        }
    }

    @Override
    @Transactional
    public DepartmentResponse create(Long tenantId, CreateDepartmentRequest request) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysDept dept = new SysDept();
            dept.setTenantId(tenantId);
            dept.setParentId(request.getParentId() == null ? 0L : request.getParentId());
            dept.setName(request.getName());
            dept.setCode(request.getCode());
            dept.setSort(request.getSort() == null ? 0 : request.getSort());
            dept.setStatus(1);
            dept.setLeaderUserId(request.getLeaderUserId());

            if (dept.getParentId() != 0L) {
                SysDept parent = deptMapper.selectById(dept.getParentId());
                if (parent == null || !parent.getTenantId().equals(tenantId)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "父部门不存在");
                }
                dept.setPath((parent.getPath() == null ? "" : parent.getPath()) + parent.getId() + "/");
                dept.setLevel(parent.getLevel() + 1);
            } else {
                dept.setPath("/");
                dept.setLevel(1);
            }

            deptMapper.insert(dept);
            return DepartmentResponse.from(dept);
        }
    }

    @Override
    @Transactional
    public DepartmentResponse update(Long tenantId, Long deptId, UpdateDepartmentRequest request) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysDept dept = deptMapper.selectById(deptId);
            if (dept == null || !dept.getTenantId().equals(tenantId)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
            }
            if (request.getName() != null) dept.setName(request.getName());
            if (request.getCode() != null) dept.setCode(request.getCode());
            if (request.getSort() != null) dept.setSort(request.getSort());
            if (request.getStatus() != null) dept.setStatus(request.getStatus());
            if (request.getLeaderUserId() != null) dept.setLeaderUserId(request.getLeaderUserId());
            if (request.getParentId() != null && !request.getParentId().equals(dept.getParentId())) {
                dept.setParentId(request.getParentId());
                if (request.getParentId() == 0L) {
                    dept.setPath("/");
                    dept.setLevel(1);
                } else {
                    SysDept parent = deptMapper.selectById(request.getParentId());
                    if (parent == null || !parent.getTenantId().equals(tenantId)) {
                        throw new BusinessException(ResultCode.NOT_FOUND, "父部门不存在");
                    }
                    dept.setPath((parent.getPath() == null ? "" : parent.getPath()) + parent.getId() + "/");
                    dept.setLevel(parent.getLevel() + 1);
                }
            }
            deptMapper.updateById(dept);
            return DepartmentResponse.from(dept);
        }
    }

    @Override
    @Transactional
    public void delete(Long tenantId, Long deptId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysDept dept = deptMapper.selectById(deptId);
            if (dept == null || !dept.getTenantId().equals(tenantId)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
            }
            long childCount = deptMapper.selectCount(Wrappers.<SysDept>lambdaQuery()
                    .eq(SysDept::getTenantId, tenantId)
                    .eq(SysDept::getParentId, deptId)
                    .eq(SysDept::getDeleted, 0));
            if (childCount > 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "该部门下仍有子部门，请先移除");
            }
            deptMapper.deleteById(deptId);
        }
    }

    @Override
    @Transactional
    public void assignUser(Long tenantId, Long userId, Long deptId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysUser user = userMapper.selectByTenantAndId(tenantId, userId);
            if (user == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
            }
            if (deptId != null && deptId != 0L) {
                SysDept dept = deptMapper.selectById(deptId);
                if (dept == null || !dept.getTenantId().equals(tenantId)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
                }
            }
            SysUser update = new SysUser();
            update.setId(userId);
            update.setDeptId(deptId);
            userMapper.updateById(update);
        }
    }

    @Override
    public List<ReportingRelationResponse> listReporting(Long tenantId, Long userId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysUserReporting> list = reportingMapper.selectList(Wrappers.<SysUserReporting>lambdaQuery()
                    .eq(SysUserReporting::getTenantId, tenantId)
                    .and(w -> w.eq(SysUserReporting::getUserId, userId)
                            .or().eq(SysUserReporting::getManagerUserId, userId))
                    .eq(SysUserReporting::getDeleted, 0));
            return list.stream().map(ReportingRelationResponse::from).toList();
        }
    }

    @Override
    @Transactional
    public ReportingRelationResponse addReporting(Long tenantId, ReportingRelationRequest request) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            if (request.getUserId().equals(request.getManagerUserId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不能设为自己的上级");
            }
            long exists = reportingMapper.selectCount(Wrappers.<SysUserReporting>lambdaQuery()
                    .eq(SysUserReporting::getTenantId, tenantId)
                    .eq(SysUserReporting::getUserId, request.getUserId())
                    .eq(SysUserReporting::getManagerUserId, request.getManagerUserId())
                    .eq(SysUserReporting::getDeleted, 0));
            if (exists > 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "该上下级关系已存在");
            }
            SysUserReporting r = new SysUserReporting();
            r.setTenantId(tenantId);
            r.setUserId(request.getUserId());
            r.setManagerUserId(request.getManagerUserId());
            r.setDeptId(request.getDeptId());
            r.setRelationType("DIRECT");
            reportingMapper.insert(r);
            return ReportingRelationResponse.from(r);
        }
    }

    @Override
    @Transactional
    public void removeReporting(Long tenantId, Long relationId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysUserReporting r = reportingMapper.selectById(relationId);
            if (r == null || !r.getTenantId().equals(tenantId)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "关系不存在");
            }
            reportingMapper.deleteById(relationId);
        }
    }

    private List<DepartmentResponse> buildTree(List<SysDept> depts) {
        Map<Long, DepartmentResponse> map = new LinkedHashMap<>();
        for (SysDept d : depts) {
            map.put(d.getId(), DepartmentResponse.from(d));
        }
        List<DepartmentResponse> roots = new ArrayList<>();
        for (DepartmentResponse dr : map.values()) {
            if (dr.getParentId() == null || dr.getParentId() == 0L) {
                roots.add(dr);
            } else {
                DepartmentResponse parent = map.get(dr.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dr);
                } else {
                    roots.add(dr);
                }
            }
        }
        return roots;
    }
}
