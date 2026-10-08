package com.zwskchat.zwskchat.datascope.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.datascope.entity.SysDataScope;
import com.zwskchat.zwskchat.datascope.mapper.SysDataScopeMapper;
import com.zwskchat.zwskchat.datascope.service.DataScopeService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.user.entity.SysDept;
import com.zwskchat.zwskchat.user.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private final SysDataScopeMapper dataScopeMapper;
    private final SysDeptMapper deptMapper;

    @Override
    public DataScopeResult resolve(String bizType) {
        LoginUser user = SecurityUtils.currentUser();

        if (user.getRoles() != null && user.getRoles().contains("SUPER_ADMIN")) {
            return new DataScopeResult("all", Set.of(), user.getUserId());
        }

        List<SysDataScope> scopes = dataScopeMapper.selectList(
                Wrappers.<SysDataScope>lambdaQuery()
                        .eq(SysDataScope::getTenantId, user.getTenantId())
                        .eq(SysDataScope::getBizType, bizType));

        String bestScope = "self";
        Set<Long> customDepts = new HashSet<>();

        for (SysDataScope scope : scopes) {
            String st = scope.getScopeType();
            if ("all".equals(st)) {
                return new DataScopeResult("all", Set.of(), user.getUserId());
            }
            if (priority(st) > priority(bestScope)) {
                bestScope = st;
            }
            if ("custom".equals(st) && scope.getCustomDeptIds() != null) {
                for (String id : scope.getCustomDeptIds().split(",")) {
                    try { customDepts.add(Long.parseLong(id.trim())); } catch (NumberFormatException ignored) {}
                }
            }
        }

        Set<Long> visibleDepts = new HashSet<>();
        if ("dept_and_children".equals(bestScope) || "dept".equals(bestScope)) {
            Long userDeptId = getUserDeptId(user);
            if (userDeptId != null) {
                visibleDepts.add(userDeptId);
                if ("dept_and_children".equals(bestScope)) {
                    visibleDepts.addAll(getChildDeptIds(user.getTenantId(), userDeptId));
                }
            }
        } else if ("custom".equals(bestScope)) {
            visibleDepts = customDepts;
        }

        return new DataScopeResult(bestScope, visibleDepts, user.getUserId());
    }

    @Override
    public List<SysDataScope> listByRole(Long roleId) {
        return dataScopeMapper.selectList(
                Wrappers.<SysDataScope>lambdaQuery()
                        .eq(SysDataScope::getRoleId, roleId));
    }

    @Override
    public void save(SysDataScope dataScope) {
        LoginUser user = SecurityUtils.currentUser();
        dataScope.setTenantId(user.getTenantId());
        if (dataScope.getId() == null) {
            dataScopeMapper.insert(dataScope);
        } else {
            dataScopeMapper.updateById(dataScope);
        }
    }

    @Override
    public void delete(Long id) {
        dataScopeMapper.deleteById(id);
    }

    private int priority(String scopeType) {
        return switch (scopeType) {
            case "all" -> 4;
            case "dept_and_children" -> 3;
            case "dept" -> 2;
            case "custom" -> 2;
            case "self" -> 1;
            default -> 0;
        };
    }

    private Long getUserDeptId(LoginUser user) {
        var u = deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .eq(SysDept::getTenantId, user.getTenantId())
                .last("LIMIT 1"));
        return u.isEmpty() ? null : u.get(0).getId();
    }

    private Set<Long> getChildDeptIds(Long tenantId, Long parentId) {
        List<SysDept> allDepts = deptMapper.selectList(
                Wrappers.<SysDept>lambdaQuery().eq(SysDept::getTenantId, tenantId));
        Set<Long> result = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(parentId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            for (SysDept d : allDepts) {
                if (Objects.equals(d.getParentId(), current) && !result.contains(d.getId())) {
                    result.add(d.getId());
                    queue.add(d.getId());
                }
            }
        }
        return result;
    }
}
