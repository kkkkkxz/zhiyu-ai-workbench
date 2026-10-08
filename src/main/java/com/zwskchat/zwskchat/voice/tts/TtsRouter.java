package com.zwskchat.zwskchat.voice.tts;

import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsRouter {

    private final VoiceProperties props;
    private final List<TtsProvider> providers;

    /** Provider name -> bean，{@link #init()} 时构造一次，每次 get() 直接命中。 */
    private Map<String, TtsProvider> byName;

    @PostConstruct
    void init() {
        Map<String, TtsProvider> map = new HashMap<>(providers.size() * 2);
        for (TtsProvider p : providers) {
            String key = p.name() == null ? "" : p.name().toLowerCase();
            map.putIfAbsent(key, p);
        }
        this.byName = Map.copyOf(map);
        log.info("[Voice] TTS providers registered: {}", byName.keySet());
    }

    public TtsProvider get() {
        String want = props.getTtsProvider() == null ? "mock" : props.getTtsProvider().toLowerCase();
        TtsProvider p = byName.get(want);
        if (p == null) {
            log.warn("[Voice] TTS provider '{}' not found, fallback to mock. available={}", want, byName.keySet());
            p = byName.get("mock");
        }
        if (p == null) throw new IllegalStateException("没有可用的 TTS Provider");
        return p;
    }
}
