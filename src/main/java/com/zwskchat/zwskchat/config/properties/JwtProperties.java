package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.security.jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenTtlSeconds = 7200;
    private long refreshTokenTtlSeconds = 604800;
    private String issuer = "zwsk-voice-rag";
}
