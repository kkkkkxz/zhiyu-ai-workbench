package com.zwskchat.zwskchat.common.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback 日志脱敏：避免明文密码、token、apiKey、手机号、邮箱、身份证号写入日志。
 */
public class SensitiveDataConverter extends ClassicConverter {

    private static final Pattern[] PATTERNS = new Pattern[] {
            // password=xxx / "password":"xxx"
            Pattern.compile("(?i)(\"?password\"?\\s*[:=]\\s*\"?)([^\",\\s}]+)"),
            // token=xxx / Authorization: Bearer xxx
            Pattern.compile("(?i)(\"?(?:access_?token|refresh_?token|token)\"?\\s*[:=]\\s*\"?)([^\",\\s}]+)"),
            Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._\\-]+)"),
            // apiKey / sk-xxx
            Pattern.compile("(?i)(\"?api[_-]?key\"?\\s*[:=]\\s*\"?)([^\",\\s}]+)"),
            Pattern.compile("(sk-[A-Za-z0-9]{6,})"),
            // 中国大陆手机号
            Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)"),
            // 身份证号
            Pattern.compile("(?<!\\d)(\\d{4})\\d{10}(\\d{4}[\\dXx])(?!\\d)"),
    };

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null || msg.isEmpty()) return "";
        String result = msg;
        // password / token / apikey
        result = PATTERNS[0].matcher(result).replaceAll("$1***");
        result = PATTERNS[1].matcher(result).replaceAll("$1***");
        result = PATTERNS[2].matcher(result).replaceAll("$1***");
        result = PATTERNS[3].matcher(result).replaceAll("$1***");
        result = PATTERNS[4].matcher(result).replaceAll("sk-***");
        // phone
        result = PATTERNS[5].matcher(result).replaceAll("$1****$3");
        // idcard
        result = PATTERNS[6].matcher(result).replaceAll("$1**********$2");
        return result;
    }
}
