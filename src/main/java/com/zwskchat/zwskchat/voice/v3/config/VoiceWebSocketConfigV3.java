package com.zwskchat.zwskchat.voice.v3.config;

import com.zwskchat.zwskchat.config.properties.SecurityProperties;
import com.zwskchat.zwskchat.voice.v3.security.VoiceV3HandshakeInterceptor;
import com.zwskchat.zwskchat.voice.v3.websocket.VoiceWebSocketHandlerV3;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VoiceWebSocketConfigV3 implements WebSocketConfigurer {
    private final VoiceWebSocketHandlerV3 voiceWebSocketHandlerV3;
    private final VoiceV3HandshakeInterceptor voiceV3HandshakeInterceptor;
    private final SecurityProperties securityProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> wsOrigins = securityProperties.getCors().getWsAllowedOrigins();
        if (wsOrigins == null || wsOrigins.isEmpty()) {
            wsOrigins = securityProperties.getCors().getAllowedOrigins();
        }
        if (wsOrigins == null || wsOrigins.isEmpty()) {
            wsOrigins = List.of("http://localhost:*", "http://127.0.0.1:*");
        }
        registry.addHandler(voiceWebSocketHandlerV3, "/voice/ws/v3")
                .addInterceptors(voiceV3HandshakeInterceptor)
                .setAllowedOriginPatterns(wsOrigins.toArray(new String[0]));
    }

    @Bean
    public ServletServerContainerFactoryBean voiceV3WebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(256 * 1024);
        return container;
    }
}
