package com.zwskchat.zwskchat.voice.asr.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import com.zwskchat.zwskchat.voice.asr.AsrCallback;
import com.zwskchat.zwskchat.voice.asr.AsrProvider;
import com.zwskchat.zwskchat.voice.asr.AsrSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容 Whisper（POST {base}/audio/transcriptions）非流式实现：
 * 在客户端 END 时将累计的 PCM 包装成 16kHz mono WAV，整段发送给服务端。
 * 适合短语音；流式 ASR 请使用厂商专用协议（DashScope Paraformer / 腾讯一句话）的 provider。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiWhisperAsrProvider implements AsrProvider {

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

    @Override
    public String name() { return "openai-whisper"; }

    @Override
    public AsrSession open(int sampleRate, AsrCallback cb) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        AtomicBoolean closed = new AtomicBoolean();

        return new AsrSession() {
            @Override
            public synchronized void sendAudio(byte[] pcm) {
                if (closed.get() || pcm == null) return;
                buf.write(pcm, 0, pcm.length);
            }

            @Override
            public void endOfStream() {
                if (!closed.compareAndSet(false, true)) return;
                executor.execute(() -> {
                    try {
                        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
                        String apiKey = tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()
                                ? tenantConfig.getApiKey()
                                : props.getOpenai().getApiKey();
                        String baseUrl = tenantConfig != null && tenantConfig.getBaseUrl() != null && !tenantConfig.getBaseUrl().isBlank()
                                ? tenantConfig.getBaseUrl()
                                : props.getOpenai().getBaseUrl();
                        if (apiKey == null || apiKey.isBlank()) {
                            cb.onError(new IllegalStateException("Whisper API Key 未配置"));
                            return;
                        }
                        byte[] pcm = buf.toByteArray();
                        if (pcm.length < sampleRate * 2 / 4) { // < 0.25s 视为空
                            cb.onFinal("");
                            return;
                        }
                        byte[] wav = wrapWav(pcm, sampleRate, 1, 16);
                        String text = transcribe(wav, baseUrl, apiKey);
                        cb.onFinal(text == null ? "" : text);
                    } catch (Exception e) {
                        cb.onError(e);
                    }
                });
            }

            @Override
            public void cancel() {
                closed.set(true);
            }
        };
    }

    private String transcribe(byte[] wav, String baseUrl, String apiKey) throws Exception {
        VoiceProperties.OpenAi o = props.getOpenai();
        String boundary = "----zwsk" + UUID.randomUUID().toString().replace("-", "");
        String url = baseUrl.replaceAll("/+$", "") + "/audio/transcriptions";

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        appendField(body, boundary, "model", o.getAsrModel());
        appendField(body, boundary, "response_format", "json");
        appendField(body, boundary, "language", "zh");
        appendFile(body, boundary, "file", "audio.wav", "audio/wav", wav);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            String b = resp.body();
            log.warn("[Whisper] non-2xx status={} body={}", resp.statusCode(),
                    b == null || b.length() < 500 ? b : b.substring(0, 500));
            throw new RuntimeException("Whisper HTTP " + resp.statusCode());
        }
        JsonNode root = om.readTree(resp.body());
        return root.path("text").asText("");
    }

    private static void appendField(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void appendFile(ByteArrayOutputStream out, String boundary, String name,
                                   String filename, String contentType, byte[] data) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name +
                "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /** 把裸 PCM 16bit signed LE 数据封装成 WAV 容器（RIFF/WAVE）。 */
    public static byte[] wrapWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;
        int chunkSize = 36 + dataSize;
        byte[] header = new byte[44];
        // RIFF header
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        writeIntLE(header, 4, chunkSize);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        // fmt chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        writeIntLE(header, 16, 16);          // PCM fmt size
        writeShortLE(header, 20, (short) 1);  // PCM
        writeShortLE(header, 22, (short) channels);
        writeIntLE(header, 24, sampleRate);
        writeIntLE(header, 28, byteRate);
        writeShortLE(header, 32, (short) blockAlign);
        writeShortLE(header, 34, (short) bitsPerSample);
        // data
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        writeIntLE(header, 40, dataSize);

        byte[] all = new byte[header.length + pcm.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(pcm, 0, all, header.length, pcm.length);
        return all;
    }

    private static void writeIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void writeShortLE(byte[] b, int off, short v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }
}
