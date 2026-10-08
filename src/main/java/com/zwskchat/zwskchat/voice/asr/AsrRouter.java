package com.zwskchat.zwskchat.voice.asr;

import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 根据配置选择 ASR Provider。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrRouter {

    private final VoiceProperties props;
    private final List<AsrProvider> providers;

    /** Provider name -> bean，{@link #init()} 时构造一次，每次 get() 直接命中。 */
    private Map<String, AsrProvider> byName;

    @PostConstruct
    void init() {
        Map<String, AsrProvider> map = new HashMap<>(providers.size() * 2);
        for (AsrProvider p : providers) {
            String key = p.name() == null ? "" : p.name().toLowerCase();
            map.putIfAbsent(key, p);
        }
        this.byName = Map.copyOf(map);
        log.info("[Voice] ASR providers registered: {}", byName.keySet());
    }

    public AsrProvider get() {
        String want = props.getAsrProvider() == null ? "mock" : props.getAsrProvider().toLowerCase();
        AsrProvider p = byName.get(want);
        if (p == null) {
            log.warn("[Voice] ASR provider '{}' not found, fallback to mock. available={}",
                    want, byName.keySet());
            p = byName.get("mock");
        }
        if (p == null) throw new IllegalStateException("没有可用的 ASR Provider");
        return p;
    }
}
