package com.zwskchat.zwskchat.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动时确保默认超级管理员可登录。
 * 仅在用户存在但密码无效（如 SQL 占位 hash）时才重置一次。
 * 生产环境强烈建议关闭此开关，并通过运维流程创建初始账号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder encoder;

    @Value("${zwsk.bootstrap.reset-admin-password:false}")
    private boolean resetAdmin;

    @Value("${zwsk.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${zwsk.bootstrap.admin-default-password:}")
    private String adminDefaultPwd;

    @Override
    public void run(String... args) {
        if (!resetAdmin || adminDefaultPwd == null || adminDefaultPwd.isBlank()) return;
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            SysUser admin = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, adminUsername));
            if (admin == null) {
                log.warn("[Bootstrap] admin user not found, skip");
                return;
            }
            // 仅当当前密码 hash 无法验证默认密码时重置（避免每次重启覆盖运维改的密码）
            if (encoder.matches(adminDefaultPwd, admin.getPassword())) {
                return;
            }
            // 进一步保险：只在 hash 看起来不是 BCrypt 时重置
            String pwd = admin.getPassword();
            boolean looksBcrypt = pwd != null && (pwd.startsWith("$2a$") || pwd.startsWith("$2b$"));
            if (looksBcrypt && !pwd.equals("$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e")) {
                return;
            }
            SysUser u = new SysUser();
            u.setId(admin.getId());
            u.setPassword(encoder.encode(adminDefaultPwd));
            u.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(u);
            log.warn("[Bootstrap] reset admin default password. Please change it after first login.");
        }
    }
}
