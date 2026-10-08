package com.zwskchat.zwskchat.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.PageResult;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.tenant.dto.CreateTenantRequest;
import com.zwskchat.zwskchat.tenant.dto.CreateTenantUserRequest;
import com.zwskchat.zwskchat.tenant.dto.RoleResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantBossStatResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantUserResponse;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantRequest;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantUserRequest;
import com.zwskchat.zwskchat.tenant.service.TenantManagementService;
import com.zwskchat.zwskchat.user.entity.SysRole;
import com.zwskchat.zwskchat.user.entity.SysRolePermission;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.entity.SysUserRole;
import com.zwskchat.zwskchat.user.mapper.SysRoleMapper;
import com.zwskchat.zwskchat.user.mapper.SysRolePermissionMapper;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantManagementServiceImpl implements TenantManagementService {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final Set<String> TENANT_ADMIN_ASSIGNABLE_ROLES =
            Set.of(ROLE_TENANT_ADMIN, "KB_ADMIN", ROLE_STAFF, "AUDITOR");

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<TenantResponse> listTenants(long page, long size, String keyword, Integer status) {
        requireSuperAdmin();
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            LambdaQueryWrapper<SysTenant> query = Wrappers.<SysTenant>lambdaQuery()
                    .eq(status != null, SysTenant::getStatus, status)
                    .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                            .like(SysTenant::getCode, keyword.trim())
                            .or()
                            .like(SysTenant::getName, keyword.trim()))
                    .orderByDesc(SysTenant::getCreateTime)
                    .orderByDesc(SysTenant::getId);
            IPage<SysTenant> result = tenantMapper.selectPage(new Page<>(normalizePage(page), normalizeSize(size)), query);
            List<TenantResponse> records = result.getRecords().stream()
                    .map(TenantResponse::from)
                    .toList();
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantResponse createTenant(CreateTenantRequest request) {
        requireSuperAdmin();
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysTenant tenant = new SysTenant();
            tenant.setCode(request.getCode().trim());
            tenant.setName(request.getName().trim());
            tenant.setStatus(STATUS_ENABLED);
            tenant.setPlanCode(defaultString(request.getPlanCode(), "standard"));
            tenant.setMaxUsers(defaultInteger(request.getMaxUsers(), 100));
            tenant.setMaxStorageMb(defaultLong(request.getMaxStorageMb(), 10240L));
            tenant.setExpiresAt(request.getExpiresAt());
            tenant.setRemark(blankToNull(request.getRemark()));
            try {
                tenantMapper.insert(tenant);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "租户编码已存在");
            }
            seedDefaultRoles(tenant.getId());
            return TenantResponse.from(tenantMapper.selectById(tenant.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantResponse updateTenant(Long tenantId, UpdateTenantRequest request) {
        requireSuperAdmin();
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysTenant existing = requireTenant(tenantId);
            SysTenant tenant = new SysTenant();
            tenant.setId(existing.getId());
            if (request.getName() != null) {
                tenant.setName(request.getName().trim());
            }
            if (request.getStatus() != null) {
                tenant.setStatus(request.getStatus());
            }
            if (request.getPlanCode() != null) {
                tenant.setPlanCode(defaultString(request.getPlanCode(), "standard"));
            }
            if (request.getMaxUsers() != null) {
                tenant.setMaxUsers(request.getMaxUsers());
            }
            if (request.getMaxStorageMb() != null) {
                tenant.setMaxStorageMb(request.getMaxStorageMb());
            }
            if (request.getExpiresAt() != null) {
                tenant.setExpiresAt(request.getExpiresAt());
            }
            if (request.getRemark() != null) {
                tenant.setRemark(blankToNull(request.getRemark()));
            }
            tenantMapper.updateById(tenant);
            return TenantResponse.from(tenantMapper.selectExistingById(tenantId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenant(Long tenantId) {
        requireSuperAdmin();
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getTenantId().equals(tenantId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能删除当前登录租户");
        }
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            requireTenant(tenantId);
            tenantMapper.deleteById(tenantId);
        }
    }

    @Override
    public PageResult<TenantUserResponse> listUsers(Long tenantId, long page, long size, String keyword, Integer status) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            LambdaQueryWrapper<SysUser> query = Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, scopedTenantId)
                    .eq(status != null, SysUser::getStatus, status)
                    .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                            .like(SysUser::getUsername, keyword.trim())
                            .or()
                            .like(SysUser::getNickname, keyword.trim())
                            .or()
                            .like(SysUser::getEmail, keyword.trim()))
                    .orderByDesc(SysUser::getCreateTime)
                    .orderByDesc(SysUser::getId);
            IPage<SysUser> result = userMapper.selectPage(new Page<>(normalizePage(page), normalizeSize(size)), query);
            List<TenantUserResponse> records = result.getRecords().stream()
                    .map(user -> TenantUserResponse.from(user, listRoleCodes(scopedTenantId, user.getId())))
                    .toList();
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantUserResponse createUser(Long tenantId, CreateTenantUserRequest request) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        Set<String> roleCodes = normalizeRoleCodes(request.getRoleCodes(), ROLE_STAFF);
        assertAssignableRoles(roleCodes);
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysTenant tenant = requireTenant(scopedTenantId);
            assertUserQuota(tenant);
            if (userMapper.countByTenantAndUsername(scopedTenantId, request.getUsername().trim()) > 0) {
                throw new BusinessException(ResultCode.USERNAME_EXISTS);
            }
            SysUser user = new SysUser();
            user.setTenantId(scopedTenantId);
            user.setUsername(request.getUsername().trim());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setNickname(defaultString(request.getNickname(), request.getUsername().trim()));
            user.setEmail(blankToNull(request.getEmail()));
            user.setPhone(blankToNull(request.getPhone()));
            user.setStatus(STATUS_ENABLED);
            userMapper.insert(user);
            replaceUserRoles(scopedTenantId, user.getId(), roleCodes);
            return TenantUserResponse.from(userMapper.selectByTenantAndId(scopedTenantId, user.getId()),
                    listRoleCodes(scopedTenantId, user.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantUserResponse updateUser(Long tenantId, Long userId, UpdateTenantUserRequest request) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysUser existing = requireUser(scopedTenantId, userId);
            SysUser user = new SysUser();
            user.setId(existing.getId());
            if (request.getNickname() != null) {
                user.setNickname(blankToNull(request.getNickname()));
            }
            if (request.getEmail() != null) {
                user.setEmail(blankToNull(request.getEmail()));
            }
            if (request.getPhone() != null) {
                user.setPhone(blankToNull(request.getPhone()));
            }
            if (request.getStatus() != null) {
                assertCanChangeStatus(existing, request.getStatus());
                user.setStatus(request.getStatus());
            }
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            userMapper.updateById(user);
            if (request.getRoleCodes() != null) {
                Set<String> roleCodes = normalizeRoleCodes(request.getRoleCodes(), ROLE_STAFF);
                assertAssignableRoles(roleCodes);
                assertCanChangeRoles(existing, roleCodes);
                replaceUserRoles(scopedTenantId, existing.getId(), roleCodes);
            }
            return TenantUserResponse.from(userMapper.selectByTenantAndId(scopedTenantId, existing.getId()),
                    listRoleCodes(scopedTenantId, existing.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long tenantId, Long userId) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能删除当前登录用户");
        }
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysUser existing = requireUser(scopedTenantId, userId);
            if (!currentUser.getRoles().contains(ROLE_SUPER_ADMIN)
                    && listRoleCodes(scopedTenantId, existing.getId()).contains(ROLE_TENANT_ADMIN)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "租户管理员不能删除其他租户管理员");
            }
            userMapper.deleteById(existing.getId());
            List<SysUserRole> links = userRoleMapper.selectByTenantAndUserId(scopedTenantId, existing.getId());
            for (SysUserRole link : links) {
                userRoleMapper.deleteById(link.getId());
            }
        }
    }

    @Override
    public List<RoleResponse> listRoles(Long tenantId) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            return roleMapper.selectEnabledByTenant(scopedTenantId).stream()
                    .filter(role -> SecurityUtils.currentUser().getRoles().contains(ROLE_SUPER_ADMIN)
                            || !ROLE_SUPER_ADMIN.equals(role.getRoleCode()))
                    .map(RoleResponse::from)
                    .toList();
        }
    }

    @Override
    public List<TenantBossStatResponse> listTenantBossStats() {
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getRoles() == null || !currentUser.getRoles().contains(ROLE_TENANT_ADMIN)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .ne(SysTenant::getCode, "platform")
                    .orderByAsc(SysTenant::getId));
            return tenants.stream()
                    .map(tenant -> TenantBossStatResponse.builder()
                            .tenantId(tenant.getId())
                            .tenantCode(tenant.getCode())
                            .tenantName(tenant.getName())
                            .status(tenant.getStatus())
                            .superAdminCount(countRoleUsers(tenant.getId(), ROLE_SUPER_ADMIN))
                            .build())
                    .toList();
        }
    }

    private Long countRoleUsers(Long tenantId, String roleCode) {
        SysRole role = roleMapper.selectByTenantAndCode(tenantId, roleCode);
        if (role == null) {
            return 0L;
        }
        return userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getRoleId, role.getId()));
    }

    private void seedDefaultRoles(Long tenantId) {
        List<SysRole> roles = List.of(
                buildRole(tenantId, ROLE_TENANT_ADMIN, "租户管理员", "管理本租户用户、角色与业务资源"),
                buildRole(tenantId, "KB_ADMIN", "知识库管理员", "管理本租户知识库"),
                buildRole(tenantId, ROLE_STAFF, "普通员工", "使用聊天、语音与知识库"),
                buildRole(tenantId, "AUDITOR", "审计员", "查看租户审计数据")
        );
        for (SysRole role : roles) {
            roleMapper.insert(role);
        }
        copyTemplateRolePermissions(tenantId);
    }

    private SysRole buildRole(Long tenantId, String roleCode, String roleName, String description) {
        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setStatus(STATUS_ENABLED);
        return role;
    }

    private void copyTemplateRolePermissions(Long tenantId) {
        for (String roleCode : TENANT_ADMIN_ASSIGNABLE_ROLES) {
            SysRole templateRole = roleMapper.selectByTenantAndCode(1L, roleCode);
            SysRole tenantRole = roleMapper.selectByTenantAndCode(tenantId, roleCode);
            if (templateRole == null || tenantRole == null) {
                continue;
            }
            List<SysRolePermission> templateLinks = rolePermissionMapper.selectList(
                    Wrappers.<SysRolePermission>lambdaQuery()
                            .eq(SysRolePermission::getRoleId, templateRole.getId()));
            for (SysRolePermission templateLink : templateLinks) {
                SysRolePermission link = new SysRolePermission();
                link.setRoleId(tenantRole.getId());
                link.setPermissionId(templateLink.getPermissionId());
                link.setCreateTime(LocalDateTime.now());
                rolePermissionMapper.insert(link);
            }
        }
    }

    private void replaceUserRoles(Long tenantId, Long userId, Set<String> roleCodes) {
        List<SysUserRole> existingLinks = userRoleMapper.selectByTenantAndUserId(tenantId, userId);
        for (SysUserRole link : existingLinks) {
            userRoleMapper.deleteById(link.getId());
        }
        Map<String, SysRole> rolesByCode = roleMapper.selectEnabledByTenant(tenantId).stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, Function.identity()));
        List<String> missingRoles = roleCodes.stream()
                .filter(roleCode -> !rolesByCode.containsKey(roleCode))
                .toList();
        if (!missingRoles.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色不存在或已停用：" + String.join(",", missingRoles));
        }
        for (String roleCode : roleCodes) {
            SysUserRole link = new SysUserRole();
            link.setTenantId(tenantId);
            link.setUserId(userId);
            link.setRoleId(rolesByCode.get(roleCode).getId());
            link.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(link);
        }
    }

    private Set<String> listRoleCodes(Long tenantId, Long userId) {
        List<SysRole> roles = roleMapper.selectRolesByTenantAndUserId(tenantId, userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }
        return roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void assertUserQuota(SysTenant tenant) {
        Integer maxUsers = tenant.getMaxUsers();
        if (maxUsers == null || maxUsers <= 0) {
            return;
        }
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenant.getId())
                .eq(SysUser::getStatus, STATUS_ENABLED));
        if (count != null && count >= maxUsers) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "租户用户数已达到上限");
        }
    }

    private Long resolveManageableTenantId(Long requestedTenantId) {
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getRoles().contains(ROLE_SUPER_ADMIN)) {
            Long tenantId = requestedTenantId == null ? currentUser.getTenantId() : requestedTenantId;
            try (TenantContext.Scope ignored = TenantContext.ignore()) {
                requireTenant(tenantId);
            }
            return tenantId;
        }
        if (!currentUser.getRoles().contains(ROLE_TENANT_ADMIN)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Long currentTenantId = currentUser.getTenantId();
        if (requestedTenantId != null && !currentTenantId.equals(requestedTenantId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能管理其他租户");
        }
        return currentTenantId;
    }

    private void requireSuperAdmin() {
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getRoles() == null || !currentUser.getRoles().contains(ROLE_SUPER_ADMIN)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private SysTenant requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "租户ID不能为空");
        }
        SysTenant tenant = tenantMapper.selectExistingById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ResultCode.TENANT_NOT_FOUND);
        }
        return tenant;
    }

    private SysUser requireUser(Long tenantId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户ID不能为空");
        }
        SysUser user = userMapper.selectByTenantAndId(tenantId, userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void assertAssignableRoles(Set<String> roleCodes) {
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getRoles().contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        List<String> invalidRoles = roleCodes.stream()
                .filter(roleCode -> !TENANT_ADMIN_ASSIGNABLE_ROLES.contains(roleCode))
                .toList();
        if (!invalidRoles.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能授予该角色：" + String.join(",", invalidRoles));
        }
    }

    private void assertCanChangeRoles(SysUser user, Set<String> newRoleCodes) {
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getRoles().contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        Set<String> existingRoles = listRoleCodes(user.getTenantId(), user.getId());
        if (existingRoles.contains(ROLE_TENANT_ADMIN) && !currentUser.getUserId().equals(user.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "租户管理员不能修改其他租户管理员角色");
        }
        if (currentUser.getUserId().equals(user.getId()) && !newRoleCodes.contains(ROLE_TENANT_ADMIN)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能移除自己的租户管理员角色");
        }
    }

    private void assertCanChangeStatus(SysUser user, Integer newStatus) {
        LoginUser currentUser = SecurityUtils.currentUser();
        if (currentUser.getUserId().equals(user.getId()) && STATUS_DISABLED == newStatus) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能停用当前登录用户");
        }
    }

    private Set<String> normalizeRoleCodes(Set<String> roleCodes, String defaultRoleCode) {
        Set<String> normalized = roleCodes == null ? new HashSet<>() : new HashSet<>(roleCodes);
        normalized = normalized.stream()
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            normalized.add(defaultRoleCode);
        }
        return normalized;
    }

    private long normalizePage(long page) {
        return page < 1 ? 1 : page;
    }

    private long normalizeSize(long size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Long defaultLong(Long value, Long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
