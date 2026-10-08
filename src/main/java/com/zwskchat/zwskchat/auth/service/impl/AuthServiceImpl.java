package com.zwskchat.zwskchat.auth.service.impl;

import com.zwskchat.zwskchat.auth.dto.LoginRequest;
import com.zwskchat.zwskchat.auth.dto.LoginResponse;
import com.zwskchat.zwskchat.auth.dto.RegisterRequest;
import com.zwskchat.zwskchat.auth.service.AuthService;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.constant.CacheKeys;
import com.zwskchat.zwskchat.common.constant.SecurityConstants;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.common.util.IpUtils;
import com.zwskchat.zwskchat.config.properties.SecurityProperties;
import com.zwskchat.zwskchat.security.JwtTokenProvider;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.TokenStore;
import com.zwskchat.zwskchat.sso.SsoTicketService;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.user.entity.SysRole;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.entity.SysUserRole;
import com.zwskchat.zwskchat.user.mapper.SysRoleMapper;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserRoleMapper;
import com.zwskchat.zwskchat.user.service.SysPermissionService;
import com.zwskchat.zwskchat.user.service.SysRoleService;
import com.zwskchat.zwskchat.user.service.SysUserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 注册时给新用户分配的默认角色编码。 */
    private static final String DEFAULT_REGISTER_ROLE = "STAFF";

    /** 系统保留用户名（不允许自助注册），防止 admin / root 被冒注。 */
    private static final Set<String> RESERVED_USERNAMES =
            Set.of("admin", "root", "system", "superadmin", "guest", "test", "anonymous");

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysPermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final TokenStore tokenStore;
    private final StringRedisTemplate redis;
    private final SecurityProperties props;
    private final SsoTicketService ssoTicketService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysTenantMapper tenantMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public LoginResponse login(LoginRequest req, HttpServletRequest httpReq) {
        String username = req.getUsername().trim();
        String tenantCode = (req.getTenantCode() == null || req.getTenantCode().isBlank())
                ? null : req.getTenantCode().trim();
        // 锁定 / 失败计数桶按 (tenantCode, username) 分；不传 tenantCode 时统一落到 "_" 槽位，
        // 行为等同旧的"全局共享桶"。多租户场景下强烈建议传 tenantCode 让桶天然隔离。
        String lockKey = CacheKeys.loginLock(tenantCode, username);
        if (Boolean.TRUE.equals(redis.hasKey(lockKey))) {
            log.warn("[Login] account locked tenant={} username={}", tenantCode, username);
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }

        // 解析具体的用户：
        // 1) 客户端传了 tenantCode → 按 (tenantCode, username) 精确查；
        // 2) 没传 tenantCode → findAllByUsername，0 行 = 用户不存在，1 行 = 直接登录，
        //    >1 行 = 抛 TENANT_CODE_REQUIRED 让客户端补传租户码后重试。
        SysUser user = resolveLoginUser(tenantCode, username);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            recordFail(tenantCode, username);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            recordFail(tenantCode, username);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }

        // 登录成功：清理失败计数 + 更新最后登录信息
        redis.delete(CacheKeys.loginFail(tenantCode, username));
        userService.updateLastLogin(user.getTenantId(), user.getId(), IpUtils.getClientIp(httpReq));

        SysTenant tenant = requireActiveTenant(user.getTenantId());
        Set<String> roles = roleService.listRoleCodesByTenantAndUserId(user.getTenantId(), user.getId());
        Set<String> perms = permissionService.listPermCodesByTenantAndUserId(user.getTenantId(), user.getId());

        LoginUser principal = LoginUser.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .username(user.getUsername())
                .password(null)
                .enabled(true)
                .locked(false)
                .roles(roles)
                .permissions(perms)
                .build();

        JwtTokenProvider.TokenPair pair = jwt.generate(principal);
        tokenStore.saveAccess(pair.accessJti(), user.getId(), jwt.getAccessTtlSeconds());
        tokenStore.saveRefresh(pair.refreshJti(), user.getId(), jwt.getRefreshTtlSeconds());

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .accessExpiresAt(pair.accessExpiresAt())
                .refreshExpiresAt(pair.refreshExpiresAt())
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .roles(roles)
                .permissions(perms)
                .build();
    }

    /**
     * 解析登录用户。
     * <ul>
     *   <li>tenantCode 非空：精确按 (tenantCode, username) 查询。</li>
     *   <li>tenantCode 为空：跨租户查找；0 行 → null（让外层抛 LOGIN_FAILED），
     *       1 行 → 返回；&gt;1 行 → 抛 TENANT_CODE_REQUIRED，让客户端引导用户补全租户码。</li>
     * </ul>
     */
    private SysUser resolveLoginUser(String tenantCode, String username) {
        if (tenantCode != null) {
            return userService.findByTenantCodeAndUsername(tenantCode, username);
        }
        java.util.List<SysUser> matches = userService.findAllByUsername(username);
        if (matches.isEmpty()) return null;
        if (matches.size() == 1) return matches.get(0);
        log.warn("[Login] username='{}' matches multiple tenants ({}), require tenantCode",
                username, matches.size());
        throw new BusinessException(ResultCode.TENANT_CODE_REQUIRED);
    }

    private void recordFail(String tenantCode, String username) {
        String key = CacheKeys.loginFail(tenantCode, username);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(props.getLogin().getLockMinutes()));
        }
        if (count != null && count >= props.getLogin().getMaxFailCount()) {
            redis.opsForValue().set(CacheKeys.loginLock(tenantCode, username), "1",
                    Duration.ofMinutes(props.getLogin().getLockMinutes()));
            log.warn("[Login] account locked due to fail-count tenant={} username={} count={}",
                    tenantCode, username, count);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest req, HttpServletRequest httpReq) {
        String username = req.getUsername().trim();
        log.info("[Register] STEP-0 enter username='{}' tenantCode='{}' ip={}",
                username, req.getTenantCode(), IpUtils.getClientIp(httpReq));

        if (RESERVED_USERNAMES.contains(username.toLowerCase())) {
            log.warn("[Register] REJECT reserved username='{}'", username);
            throw new BusinessException(ResultCode.BAD_REQUEST, "该用户名为系统保留名，禁止注册");
        }

        // 1. 解析租户：新注册必须显式选择 tenantId，避免员工误注册到默认企业
        SysTenant tenant = resolveRegisterTenant(req.getTenantId(), req.getTenantCode());
        if (tenant == null) {
            log.warn("[Register] STEP-1 tenant not found, requested='{}'", req.getTenantCode());
            throw new BusinessException(ResultCode.TENANT_NOT_FOUND);
        }
        Long tenantId = tenant.getId();
        log.info("[Register] STEP-1 tenant resolved id={} code={}", tenantId, tenant.getCode());

        // 2. 同租户 username 占用预检（DB 唯一约束兜底，但提前出友好错误）
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            int dup = userMapper.countByTenantAndUsername(tenantId, username);
            log.info("[Register] STEP-2 duplicate-check tenantId={} username='{}' count={}",
                    tenantId, username, dup);
            if (dup > 0) {
                throw new BusinessException(ResultCode.USERNAME_EXISTS);
            }

            // 3. 解析默认角色，避免硬编码 role_id
            SysRole defaultRole = roleMapper.selectByTenantAndCode(tenantId, DEFAULT_REGISTER_ROLE);
            if (defaultRole == null) {
                log.error("[Register] STEP-3 default role '{}' missing for tenantId={}",
                        DEFAULT_REGISTER_ROLE, tenantId);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "默认角色未配置，请联系管理员");
            }
            log.info("[Register] STEP-3 default role resolved id={} code={}",
                    defaultRole.getId(), defaultRole.getRoleCode());

            // 4. 落库：BCrypt 加密 + 启用状态 + 默认昵称
            SysUser user = new SysUser();
            user.setTenantId(tenantId);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            user.setNickname(blankToNull(req.getNickname()) == null ? username : req.getNickname().trim());
            user.setEmail(blankToNull(req.getEmail()));
            user.setPhone(blankToNull(req.getPhone()));
            user.setStatus(1);
            // create_by / create_time 由 MyBatis-Plus MetaObjectHandler 自动填充；
            // 但注册时 SecurityUtils.currentUserIdOrNull() 为 null，create_by 会留空，符合预期
            try {
                int rows = userMapper.insert(user);
                log.info("[Register] STEP-4 sys_user inserted rows={} generatedId={}", rows, user.getId());
            } catch (DuplicateKeyException dke) {
                log.warn("[Register] STEP-4 duplicate key on insert username='{}' tenantId={}",
                        username, tenantId);
                throw new BusinessException(ResultCode.USERNAME_EXISTS);
            }

            SysUserRole link = new SysUserRole();
            link.setTenantId(tenantId);
            link.setUserId(user.getId());
            link.setRoleId(defaultRole.getId());
            link.setCreateTime(LocalDateTime.now());
            int linkRows = userRoleMapper.insert(link);
            log.info("[Register] STEP-5 sys_user_role inserted rows={} linkId={} userId={} roleId={}",
                    linkRows, link.getId(), user.getId(), defaultRole.getId());

            // 5. 直接颁发 JWT，让前端无需再走一次登录请求
            LoginResponse resp = issueToken(user, httpReq);
            log.info("[Register] STEP-6 done, JWT issued for userId={}", user.getId());
            return resp;
        } catch (RuntimeException ex) {
            // 显式记录会触发 @Transactional 回滚的异常，方便复盘"为什么没落库"
            log.error("[Register] FAILED rolling back tx, username='{}' err={}",
                    username, ex.toString(), ex);
            throw ex;
        }
    }

    /** 注册路径解析租户：优先使用 tenantId；tenantCode 仅兼容旧客户端。 */
    private SysTenant resolveRegisterTenant(Long tenantId, String rawTenantCode) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            if (tenantId != null) {
                SysTenant tenant = tenantMapper.selectExistingById(tenantId);
                if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                    return null;
                }
                return tenant;
            }
            String code = (rawTenantCode == null || rawTenantCode.isBlank()) ? null : rawTenantCode.trim();
            return code == null ? null : tenantMapper.selectByCode(code);
        }
    }

    private SysTenant resolveTenantById(Long tenantId) {
        if (tenantId == null) return null;
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysTenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null || tenant.getDeleted() != null && tenant.getDeleted() == 1) return null;
            if (tenant.getStatus() != null && tenant.getStatus() != 1) return null;
            return tenant;
        }
    }

    private SysTenant requireActiveTenant(Long tenantId) {
        SysTenant tenant = resolveTenantById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ResultCode.TENANT_NOT_FOUND);
        }
        return tenant;
    }

    /**
     * 用已落库的 user 生成 JWT 并组装 LoginResponse。
     * 与 login() 的颁发逻辑保持一致，避免注册和登录在 token 字段上不对称。
     */
    private LoginResponse issueToken(SysUser user, HttpServletRequest httpReq) {
        userService.updateLastLogin(user.getTenantId(), user.getId(), IpUtils.getClientIp(httpReq));
        SysTenant tenant = requireActiveTenant(user.getTenantId());
        Set<String> roles = roleService.listRoleCodesByTenantAndUserId(user.getTenantId(), user.getId());
        Set<String> perms = permissionService.listPermCodesByTenantAndUserId(user.getTenantId(), user.getId());

        LoginUser principal = LoginUser.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .username(user.getUsername())
                .password(null)
                .enabled(true)
                .locked(false)
                .roles(roles)
                .permissions(perms)
                .build();

        JwtTokenProvider.TokenPair pair = jwt.generate(principal);
        tokenStore.saveAccess(pair.accessJti(), user.getId(), jwt.getAccessTtlSeconds());
        tokenStore.saveRefresh(pair.refreshJti(), user.getId(), jwt.getRefreshTtlSeconds());

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .accessExpiresAt(pair.accessExpiresAt())
                .refreshExpiresAt(pair.refreshExpiresAt())
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .roles(roles)
                .permissions(perms)
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        Claims claims = jwt.parse(refreshToken);
        if (claims == null || !SecurityConstants.TOKEN_TYPE_REFRESH.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE))) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        String jti = claims.getId();
        if (jti == null || !tokenStore.isRefreshActive(jti) || tokenStore.isBlacklisted(jti)) {
            throw new BusinessException(ResultCode.TOKEN_REVOKED);
        }
        Number userIdNum = claims.get(SecurityConstants.CLAIM_USER_ID, Number.class);
        if (userIdNum == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        Long userId = userIdNum.longValue();
        Number tenantIdNum = claims.get(SecurityConstants.CLAIM_TENANT_ID, Number.class);
        if (tenantIdNum == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        Long tenantId = tenantIdNum.longValue();
        SysUser user = userService.findByTenantAndId(tenantId, userId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 旋转：作废旧 refresh token
        tokenStore.revokeRefresh(jti);
        long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        tokenStore.blacklist(jti, Math.max(remaining, 1));

        SysTenant tenant = requireActiveTenant(user.getTenantId());
        Set<String> roles = roleService.listRoleCodesByTenantAndUserId(user.getTenantId(), user.getId());
        Set<String> perms = permissionService.listPermCodesByTenantAndUserId(user.getTenantId(), user.getId());

        LoginUser principal = LoginUser.builder()
                .userId(user.getId()).tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .username(user.getUsername())
                .enabled(true).locked(false).roles(roles).permissions(perms).build();

        JwtTokenProvider.TokenPair pair = jwt.generate(principal);
        tokenStore.saveAccess(pair.accessJti(), user.getId(), jwt.getAccessTtlSeconds());
        tokenStore.saveRefresh(pair.refreshJti(), user.getId(), jwt.getRefreshTtlSeconds());

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .accessExpiresAt(pair.accessExpiresAt())
                .refreshExpiresAt(pair.refreshExpiresAt())
                .tokenType("Bearer")
                .userId(user.getId()).username(user.getUsername()).nickname(user.getNickname())
                .tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .roles(roles).permissions(perms).build();
    }

    @Override
    public LoginResponse ssoExchange(String ticket, HttpServletRequest httpReq) {
        SsoTicketService.SsoPrincipal principal = ssoTicketService.consume(ticket);
        if (principal == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "SSO 票据无效或已过期");
        }
        SysUser user = userService.findByTenantAndId(principal.tenantId(), principal.userId());
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        log.info("[SSO] exchange success tenantId={}, userId={}", principal.tenantId(), principal.userId());
        return issueToken(user, httpReq);
    }

    @Override
    public LoginResponse oidcLogin(String tenantCode, String username, HttpServletRequest httpReq) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "OIDC 身份缺少用户名标识");
        }
        // 按租户 + 用户名精确匹配已有账号；不自动建号，避免外部 IdP 把任意身份注入系统。
        SysUser user = (tenantCode == null || tenantCode.isBlank())
                ? userService.findByUsername(username.trim())
                : userService.findByTenantCodeAndUsername(tenantCode.trim(), username.trim());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND,
                    "OIDC 身份未在系统中开通对应账号，请联系管理员预先创建");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        log.info("[OIDC] login success tenantCode={}, username={}, userId={}", tenantCode, username, user.getId());
        return issueToken(user, httpReq);
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken == null) return;
        if (accessToken.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            accessToken = accessToken.substring(SecurityConstants.TOKEN_PREFIX.length());
        }
        Claims claims = jwt.parse(accessToken);
        if (claims == null) return;
        String jti = claims.getId();
        if (jti != null) {
            long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
            // 先加入黑名单，再撤销 token，避免竞态条件
            tokenStore.blacklist(jti, Math.max(remaining, 1));
            tokenStore.revokeAccess(jti);
        }
    }
}
