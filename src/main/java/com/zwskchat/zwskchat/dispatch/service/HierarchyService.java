package com.zwskchat.zwskchat.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwskchat.zwskchat.user.entity.SysDept;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.entity.SysUserReporting;
import com.zwskchat.zwskchat.user.mapper.SysDeptMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserReportingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 层级校验服务：判断某用户是否是另一用户的上级
 */
@Service
@RequiredArgsConstructor
public class HierarchyService {

    private final SysUserReportingMapper reportingMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    /**
     * 判断 managerId 是否是 subordinateId 的上级。
     * 判断逻辑（满足任一即为上级）：
     * 1. sys_user_reporting 表有直接汇报关系
     * 2. managerId 所在部门是 subordinateId 所在部门的父部门（部门层级）
     */
    public boolean isSuperior(Long managerId, Long subordinateId) {
        if (managerId == null || subordinateId == null) return false;
        if (managerId.equals(subordinateId)) return false;

        // 1. 直接汇报关系
        long count = reportingMapper.selectCount(
                new LambdaQueryWrapper<SysUserReporting>()
                        .eq(SysUserReporting::getUserId, subordinateId)
                        .eq(SysUserReporting::getManagerUserId, managerId));
        if (count > 0) return true;

        // 2. 部门层级：manager 的部门是 subordinate 部门的祖先
        SysUser manager = userMapper.selectById(managerId);
        SysUser subordinate = userMapper.selectById(subordinateId);
        if (manager == null || subordinate == null) return false;
        if (manager.getDeptId() == null || subordinate.getDeptId() == null) return false;
        if (manager.getDeptId().equals(subordinate.getDeptId())) {
            // 同一部门：看 reporting 关系（已检查过），不算上级
            return false;
        }
        return isDeptAncestor(manager.getDeptId(), subordinate.getDeptId());
    }

    /**
     * 判断 ancestorDeptId 是否是 childDeptId 的祖先部门
     */
    private boolean isDeptAncestor(Long ancestorDeptId, Long childDeptId) {
        Long current = childDeptId;
        int depth = 0;
        while (current != null && current != 0 && depth < 10) {
            SysDept dept = deptMapper.selectById(current);
            if (dept == null) return false;
            if (dept.getParentId() != null && dept.getParentId().equals(ancestorDeptId)) {
                return true;
            }
            current = dept.getParentId();
            depth++;
        }
        return false;
    }

    /**
     * 通过姓名/昵称模糊匹配用户
     */
    public SysUser findUserByName(Long tenantId, String name) {
        if (name == null || name.isBlank()) return null;
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getStatus, 1)
                        .and(w -> w.like(SysUser::getNickname, name).or().like(SysUser::getUsername, name))
                        .last("LIMIT 5"));
        return users.isEmpty() ? null : users.get(0);
    }

    /**
     * 通过部门名称模糊匹配部门
     */
    public SysDept findDeptByName(Long tenantId, String deptName) {
        if (deptName == null || deptName.isBlank()) return null;
        List<SysDept> depts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getTenantId, tenantId)
                        .like(SysDept::getName, deptName)
                        .last("LIMIT 3"));
        return depts.isEmpty() ? null : depts.get(0);
    }

    /**
     * 查找部门下的某个用户
     */
    public SysUser findUserInDept(Long tenantId, Long deptId, String userName) {
        if (deptId == null || userName == null) return null;
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getDeptId, deptId)
                        .eq(SysUser::getStatus, 1)
                        .and(w -> w.like(SysUser::getNickname, userName).or().like(SysUser::getUsername, userName))
                        .last("LIMIT 3"));
        return users.isEmpty() ? null : users.get(0);
    }
}
