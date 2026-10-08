package com.zwskchat.zwskchat.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动期安全配置体检：
 * <ul>
 *   <li>JWT secret 不能是源码仓库里的 dev 默认值</li>
 *   <li>核心中间件密码不能使用已知弱默认值</li>
 * </ul>
 *
 * <p>在 <code>prod</code> profile 下任一项不通过都会抛异常拒绝启动；
 * 在 <code>dev/test</code> 下只打 WARN 日志，不影响本地开发体验。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSecurityCheck implements ApplicationRunner {

    private static final String DEV_JWT_DEFAULT =
            "dev-only-secret-key-please-change-in-production-min-64-bytes-required!!";

    /** 已知弱默认值（属性 key → 期望禁用的默认值）。 */
    private static final Map<String, List<String>> WEAK_DEFAULTS = new LinkedHashMap<>() {{
        put("spring.datasource.password",            List.of("zwsk_dev_pwd", "root", "123456", ""));
        put("spring.rabbitmq.password",              List.of("zwsk_dev_pwd", "guest", ""));
        put("zwsk.storage.minio.access-key",         List.of("minioadmin"));
        put("zwsk.storage.minio.secret-key",         List.of("minioadmin"));
        put("zwsk.security.jwt.secret",              List.of(DEV_JWT_DEFAULT, ""));
    }};

    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        StringBuilder violations = new StringBuilder();
        WEAK_DEFAULTS.forEach((key, weak) -> {
            String actual = env.getProperty(key);
            if (actual != null && weak.contains(actual)) {
                violations.append("  - ").append(key)
                        .append(" 使用了弱默认值，必须通过环境变量 / secret 文件覆盖\n");
            }
        });

        if (violations.length() == 0) {
            log.info("[Security] 启动期凭据体检通过");
            return;
        }

        String banner = "\n=========================== 安全配置不合规 ===========================\n"
                + violations
                + "===================================================================";

        if (prod) {
            log.error(banner);
            throw new IllegalStateException(
                    "生产环境检测到弱默认凭据，拒绝启动；请设置对应环境变量后再启动。");
        }
        log.warn(banner);
        log.warn("[Security] 当前为非生产 profile，弱默认仅告警；上线前必须替换。");
    }
}
