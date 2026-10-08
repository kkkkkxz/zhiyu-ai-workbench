package com.zwskchat.zwskchat.security;

import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import com.zwskchat.zwskchat.user.service.SysPermissionService;
import com.zwskchat.zwskchat.user.service.SysRoleService;
import com.zwskchat.zwskchat.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysPermissionService permissionService;
    private final SysTenantMapper tenantMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return buildLoginUser(user);
    }

    public UserDetails loadUserByTenantAndId(Long tenantId, Long userId) throws UsernameNotFoundException {
        SysUser user = userService.findByTenantAndId(tenantId, userId);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return buildLoginUser(user);
    }

    private LoginUser buildLoginUser(SysUser user) {
        Set<String> roles = roleService.listRoleCodesByTenantAndUserId(user.getTenantId(), user.getId());
        Set<String> perms = permissionService.listPermCodesByTenantAndUserId(user.getTenantId(), user.getId());
        return LoginUser.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .tenantCode(resolveTenantCode(user.getTenantId()))
                .username(user.getUsername())
                .password(user.getPassword())
                .enabled(user.getStatus() != null && user.getStatus() == 1)
                .locked(user.getStatus() != null && user.getStatus() == 2)
                .roles(roles)
                .permissions(perms)
                .build();
    }

    private String resolveTenantCode(Long tenantId) {
        if (tenantId == null) return null;
        var tenant = tenantMapper.selectById(tenantId);
        return tenant == null ? null : tenant.getCode();
    }
}
