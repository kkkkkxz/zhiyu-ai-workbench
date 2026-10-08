package com.zwskchat.zwskchat.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private Long userId;
    private Long tenantId;
    private String tenantCode;
    private String username;
    private String password;
    private boolean enabled;
    private boolean locked;
    private Set<String> roles;
    private Set<String> permissions;

    /**
     * Spring Security 授权信息。
     *
     * <p><b>命名约定（写 {@code @PreAuthorize} 时严格按此使用）：</b>
     * <ul>
     *   <li>角色码（如 {@code SUPER_ADMIN}）→ 加 {@code ROLE_} 前缀变成 GrantedAuthority；
     *       表达式用 {@code hasRole('SUPER_ADMIN')} 或 {@code hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')}，
     *       <b>不要</b>写 {@code hasAuthority('ROLE_xxx')} —— Spring 会自动加前缀。</li>
     *   <li>权限码（如 {@code kb:create / chat:stream}）→ 直接作为 GrantedAuthority；
     *       表达式用 {@code hasAuthority('kb:create')}。</li>
     * </ul></p>
     *
     * <p>历史 bug 提示：之前用了非 mutable 的 {@code Set.of()} 兜底，再 forEach 调 {@code add}
     * 会抛 {@link UnsupportedOperationException}。现已切到 HashSet 起步，确保 perm 总能被追加。</p>
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> auths = new java.util.HashSet<>();
        if (roles != null) {
            for (String r : roles) {
                if (r != null && !r.isBlank()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + r));
                }
            }
        }
        if (permissions != null) {
            for (String p : permissions) {
                if (p != null && !p.isBlank()) {
                    auths.add(new SimpleGrantedAuthority(p));
                }
            }
        }
        return auths;
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
