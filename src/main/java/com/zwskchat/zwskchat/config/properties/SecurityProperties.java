package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.security")
public class SecurityProperties {

    private List<String> permitUrls = new ArrayList<>();
    private Login login = new Login();
    private Password password = new Password();
    private Cors cors = new Cors();

    @Data
    public static class Login {
        private int maxFailCount = 5;
        private int lockMinutes = 30;
    }

    @Data
    public static class Password {
        private int bcryptStrength = 10;
    }

    /**
     * CORS 配置。默认只放本地 dev origin pattern，生产环境必须通过
     * <code>zwsk.security.cors.allowed-origins</code> 或环境变量
     * <code>CORS_ALLOWED_ORIGINS</code> 显式覆盖。
     */
    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"));
        /** WebSocket 握手阶段的 origin 白名单，默认复用 allowedOrigins。 */
        private List<String> wsAllowedOrigins = new ArrayList<>();
    }
}
