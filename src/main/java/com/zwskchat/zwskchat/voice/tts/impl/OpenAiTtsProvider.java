package com.zwskchat.zwskchat.voice.tts.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import com.zwskchat.zwskchat.voice.tts.TtsCallback;
import com.zwskchat.zwskchat.voice.tts.TtsProvider;
import com.zwskchat.zwskchat.voice.tts.TtsTask;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容 TTS（POST {base}/audio/speech）流式实现：
 * 服务端逐句调用，每次按 4KB chunk 推送给客户端，客户端按 audioFormat 解码播放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiTtsProvider implements TtsProvider {

    private final VoiceProperties props;
    private final ObjectMapper om;
    private final TenantAiConfigService tenantAiConfigService;
    @Qualifier("commonExecutor")
    private final ThreadPoolTaskExecutor executor;
    private HttpClient http;

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override public String name() { return "openai-tts"; }
    @Override public String audioFormat() { return props.getOpenai().getTtsFormat(); }

    @Override
    public TtsTask synthesize(String text, TtsCallback cb) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean done = new AtomicBoolean();
        executor.execute(() -> {
            try {
                VoiceProperties.OpenAi o = props.getOpenai();
                TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
                String apiKey = tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()
                        ? tenantConfig.getApiKey()
                        : o.getApiKey();
                String baseUrl = tenantConfig != null && tenantConfig.getBaseUrl() != null && !tenantConfig.getBaseUrl().isBlank()
                        ? tenantConfig.getBaseUrl()
                        : o.getBaseUrl();
                if (apiKey == null || apiKey.isBlank()) {
                    cb.onError(new IllegalStateException("TTS API Key 未配置"));
                    return;
                }
                Map<String, Object> body = new HashMap<>();
                body.put("model", o.getTtsModel());
                body.put("voice", o.getTtsVoice());
                body.put("input", text);
                body.put("response_format", o.getTtsFormat());

                HttpRequest req = HttpRequest.newBuilder(URI.create(
                                baseUrl.replaceAll("/+$", "") + "/audio/speech"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2) {
                    String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("[TTS] non-2xx status={} body={}", resp.statusCode(),
                            err.length() > 500 ? err.substring(0, 500) : err);
                    if (!cancelled.get()) cb.onError(new RuntimeException("TTS HTTP " + resp.statusCode()));
                    return;
                }
                try (InputStream in = resp.body()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled.get()) return;
                        byte[] chunk = new byte[n];
                        System.arraycopy(buf, 0, chunk, 0, n);
                        cb.onAudio(chunk);
                    }
                }
                if (!cancelled.get()) cb.onComplete();
            } catch (Exception e) {
                if (!cancelled.get()) cb.onError(e);
            } finally {
                done.set(true);
            }
        });
        return new TtsTask() {
            @Override public void cancel() { cancelled.set(true); }
            @Override public boolean isDone() { return done.get(); }
        };
    }
}
