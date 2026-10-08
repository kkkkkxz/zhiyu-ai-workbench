package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.voice")
public class VoiceProperties {

    /** ASR provider: mock / openai-whisper / dashscope-paraformer ... */
    private String asrProvider = "mock";
    /** TTS provider: mock / openai-tts / dashscope-cosyvoice ... */
    private String ttsProvider = "mock";

    /** 客户端上传音频采样率（标准 16000） */
    private int sampleRate = 16000;
    /** 单声道；服务端默认按 16bit PCM 解析 */
    private int channels = 1;

    /** 单次最长录音（秒），超过自动 ASR */
    private int maxRecordingSeconds = 60;
    /** WebSocket 空闲超时（秒） */
    private int sessionIdleTimeoutSeconds = 600;
    /** TTS 文本最小切句长度（字符），低于该长度不触发 TTS */
    private int ttsMinSentenceChars = 12;

    private OpenAi openai = new OpenAi();
    private DashScope dashscope = new DashScope();
    private PublicJarvis publicJarvis = new PublicJarvis();
    private ServerVad serverVad = new ServerVad();
    private WakeWord wakeWord = new WakeWord();

    /**
     * 唤醒词配置（对应工程计划书“唤醒词层建议独立……专门的本地引擎”）。
     *
     * <p>架构定位：always-on 唤醒优先由端侧本地引擎（如 Porcupine）完成——端侧检测到唤醒词后
     * 才建立/激活语音连接，避免主模型与 ASR 长时间驻留的功耗与成本。服务端这一层是
     * <b>第二道确认 + 无端侧引擎时的兜底</b>：在 HANDS_FREE 连续对话中，要求本轮 ASR 文本以
     * 唤醒词开头才真正进入 LLM；命中后剥离唤醒词再送检索/生成。</p>
     *
     * <p>客户端若已用 Porcupine 在端侧唤醒，可在 START_TURN 的 client 字段带
     * {@code {"wakeVerified": true}} 跳过服务端二次校验。</p>
     */
    @Data
    public static class WakeWord {
        /** 是否启用服务端唤醒词校验。默认关闭：PTT/点击说话场景不需要，开启后仅作用于 HANDS_FREE。 */
        private boolean enabled = false;
        /** 仅在 HANDS_FREE 模式校验唤醒词；PTT 是用户显式发起，无需唤醒。 */
        private boolean handsFreeOnly = true;
        /** 唤醒词列表（不区分大小写、忽略空白与标点）。例如：你好展望 / 展望同学 / hey jarvis。 */
        private java.util.List<String> phrases = new java.util.ArrayList<>(
                java.util.List.of("你好展望", "展望同学", "嘿展望"));
        /** 命中唤醒词后是否从送入 LLM 的文本中剥离唤醒词前缀。 */
        private boolean stripPrefix = true;
        /**
         * 未命中唤醒词时给客户端的提示（用于前端显示“请先说唤醒词”）。留空则静默结束该轮。
         */
        private String missHint = "";
    }

    /**
     * 服务端 VAD 兜底参数（HANDS_FREE 模式）。
     *
     * <p>之前固定阈值 peak=350 / rms=90 在嘈杂环境（风扇、空调、街道、人声背景）会
     * 把环境底噪当成"在说话"，永远凑不齐 silence-ms 静音 → LISTENING 卡死。
     * 现在加三层保护：</p>
     * <ol>
     *   <li>每轮起手做 {@code noiseCalibrationMs} 毫秒底噪估计；</li>
     *   <li>实际语音判定阈值 = max(静态阈值, 底噪 × 倍率)；</li>
     *   <li>{@code maxTurnMs} 硬上限，超过即使仍判定"有语音"也强制结束。</li>
     * </ol>
     */
    @Data
    public static class ServerVad {
        /** 单帧 PCM 16-bit 峰值绝对值阈值，>= 该值视为本帧含语音。 */
        private int speechPeak = 350;
        /** 单帧 PCM 16-bit RMS 阈值，>= 该值视为本帧含语音。 */
        private int speechRms = 90;
        /** 自动结束前的最低累计录音时长，避免极短噪声立刻被结束。 */
        private long minRecordingMs = 900;
        /** 检测到语音后的尾部静音时长，达到则自动 endAudio。 */
        private long silenceMs = 1400;
        /** 单轮最大录音硬上限；噪声环境兜底，超过即强制结束。 */
        private long maxTurnMs = 15000;
        /** 每轮起手用作底噪估计的时间（毫秒）。期间不做静音判定，只采集底噪样本。 */
        private long noiseCalibrationMs = 400;
        /** 自适应阈值倍率：实际 peak 阈值 = max(speechPeak, 底噪 peak × 该倍率)。 */
        private double noisePeakMultiplier = 1.8;
        /** 自适应阈值倍率：实际 rms 阈值 = max(speechRms, 底噪 rms × 该倍率)。 */
        private double noiseRmsMultiplier = 2.2;
        /** 滑动窗口大小（帧）：在该窗口内统计达标帧数。 */
        private int speechWindowFrames = 8;
        /** 滑动窗口内至少多少帧达标，才把本轮锁为 hasSpeech 并刷新 lastSpeechAt。 */
        private int speechWindowHits = 4;
    }

    @Data
    public static class OpenAi {
        /** OpenAI 兼容 ASR/TTS 网关 base URL，复用同一 key 也可分别配置 */
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey;
        private String asrModel = "whisper-1";
        private String ttsModel = "tts-1";
        private String ttsVoice = "alloy";
        /** 输出音频格式：mp3 / opus / aac / flac / wav / pcm */
        private String ttsFormat = "mp3";
    }

    @Data
    public static class DashScope {
        /** CosyVoice TTS 模型；v2 必须搭配 *_v2 或 loongstella/loongbella 等新音色 */
        private String ttsModel = "cosyvoice-v2";
        /** TTS 音色。cosyvoice-v2 可选：longxiaochun_v2 / longwan_v2 / loongstella / loongbella 等 */
        private String ttsVoice = "longxiaochun_v2";
        /** mp3 / pcm / wav 等。默认 mp3-22050Hz-mono-256kbps */
        private String ttsFormat = "mp3-22050hz-mono-256kbps";
        /** 语速，DashScope CosyVoice v2 取值范围 [0.5, 2.0]，1.0 = 模型训练原始语速。 */
        private float ttsSpeechRate = 1.0f;
        /** Paraformer ASR 模型 */
        private String asrModel = "paraformer-realtime-v2";
    }

    /**
     * 免登录全局语音 Jarvis 配置。
     *
     * <p><b>安全约束：</b>该入口跳过 JWT 鉴权，让任何人可消费 LLM/ASR/TTS。
     * 必须在以下三个维度叠加防护，否则就是"敞开 API key 烧别人的钱"的攻击面：
     * <ol>
     *   <li>{@code enabled} — 默认关闭。仅 demo 公网环境显式开启。</li>
     *   <li>{@code maxConnectionsPerIp / connectionWindowSeconds} — 单 IP 握手频率限流，
     *       由 {@code VoiceV3HandshakeInterceptor} 在握手阶段强制。</li>
     *   <li>{@code maxMessagesPerConnection} — 单条 WebSocket 连接最多处理几次 ASR_FINAL
     *       问答，由 {@code VoiceSessionV3} 计数；超出后服务端主动关闭。</li>
     * </ol></p>
     */
    @Data
    public static class PublicJarvis {
        /**
         * 是否开放免登录 Jarvis 语音入口。
         * <p><b>默认关闭</b>——历史 bug：曾默认开启 + 写死生产租户 ID + 完全无限流，
         * 攻击者只需访问 {@code /voice/ws/v3?public=1} 即可无成本消费 LLM/TTS 配额。
         * 仅在 demo 公网部署时才在 application-{profile}.yaml 显式覆盖为 true，
         * 并配合下方限流参数。</p>
         */
        private boolean enabled = false;
        /**
         * 免登录模式使用的租户边界；只检索该租户下可公开读取的知识库。
         * <p>建议改成专用 demo 租户（不要用 1L 这种真实业务租户），避免匿名访客污染
         * 真实租户的会话历史 / 误读 PUBLIC 业务数据。</p>
         */
        private Long tenantId = 0L;
        /** 免登录模式写入会话/消息时使用的系统用户 ID。 */
        private Long userId = 0L;
        private String username = "jarvis-public";

        /** 同 IP 在 {@link #connectionWindowSeconds} 内最多发起的握手次数；超出返回 429。 */
        private int maxConnectionsPerIp = 5;
        /** IP 握手限流的窗口长度（秒）。 */
        private int connectionWindowSeconds = 60;
        /**
         * 单条 WebSocket 连接累计可处理的 ASR_FINAL 问答次数。
         * <p>到达上限后服务端主动 close(POLICY_VIOLATION) 让客户端重新建连——
         * 重新建连又会被 {@link #maxConnectionsPerIp} 卡住，组合形成"匿名预算"。</p>
         */
        private int maxMessagesPerConnection = 30;
    }
}
