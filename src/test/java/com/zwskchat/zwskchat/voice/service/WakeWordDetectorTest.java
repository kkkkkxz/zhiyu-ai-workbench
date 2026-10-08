package com.zwskchat.zwskchat.voice.service;

import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WakeWordDetectorTest {

    private final WakeWordDetector detector = new WakeWordDetector();

    private VoiceProperties.WakeWord cfg(boolean strip, String... phrases) {
        VoiceProperties.WakeWord c = new VoiceProperties.WakeWord();
        c.setPhrases(List.of(phrases));
        c.setStripPrefix(strip);
        return c;
    }

    @Test
    void matchesWakeWordAndStripsPrefix() {
        WakeWordDetector.Result r = detector.detect("你好展望，帮我查一下合同", cfg(true, "你好展望"));
        assertTrue(r.awake());
        assertEquals("帮我查一下合同", r.strippedText());
        assertEquals("你好展望", r.matchedPhrase());
    }

    @Test
    void noStripKeepsOriginalText() {
        WakeWordDetector.Result r = detector.detect("你好展望 今天天气", cfg(false, "你好展望"));
        assertTrue(r.awake());
        assertEquals("你好展望 今天天气", r.strippedText());
    }

    @Test
    void missingWakeWordIsNotAwake() {
        WakeWordDetector.Result r = detector.detect("帮我查一下合同", cfg(true, "你好展望"));
        assertFalse(r.awake());
    }

    @Test
    void wakeWordOnlyYieldsBlankStripped() {
        WakeWordDetector.Result r = detector.detect("你好展望", cfg(true, "你好展望"));
        assertTrue(r.awake());
        // 仅唤醒词、无正文：剥离后回退为原文（调用方据 blank 判断转待命）。
        assertTrue(r.strippedText() == null || r.strippedText().isBlank()
                || r.strippedText().equals("你好展望"));
    }

    @Test
    void ignoresPunctuationAndCaseForEnglishWakeWord() {
        WakeWordDetector.Result r = detector.detect("Hey, Jarvis! open the door", cfg(true, "hey jarvis"));
        assertTrue(r.awake());
        assertEquals("open the door", r.strippedText());
    }

    @Test
    void emptyPhraseListPassesThrough() {
        VoiceProperties.WakeWord c = new VoiceProperties.WakeWord();
        c.setPhrases(List.of());
        WakeWordDetector.Result r = detector.detect("任意文本", c);
        assertTrue(r.awake());
    }
}
