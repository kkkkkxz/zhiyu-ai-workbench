package com.zwskchat.zwskchat.storage;

import com.zwskchat.zwskchat.config.properties.MinioProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 构造 MinIO 对象 Key。
 *
 * <p>规则：{@code {keyPrefix}/{tenantId}/{kbId}/{yyyy}/{MM}/{dd}/{hash8}-{safeFileName}}</p>
 *
 * <ul>
 *   <li>{@code keyPrefix} 来自 {@link MinioProperties#getKeyPrefix()}，便于多业务共桶</li>
 *   <li>{@code hash8} 是 SHA-256 前 8 位，加在前面避免同名文件覆盖</li>
 *   <li>{@code safeFileName} 移除路径分隔符 / 控制字符 / 首尾空白</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ObjectKeyBuilder {

    private static final DateTimeFormatter Y = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter M = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd");

    private final MinioProperties props;

    public String build(Long tenantId, Long kbId, String fileHash, String fileName) {
        LocalDate today = LocalDate.now();
        String safe = sanitize(fileName);
        String hash8 = (fileHash == null ? "00000000" : fileHash.substring(0, Math.min(8, fileHash.length())));
        StringBuilder sb = new StringBuilder();
        if (props.getKeyPrefix() != null && !props.getKeyPrefix().isBlank()) {
            sb.append(props.getKeyPrefix()).append('/');
        }
        sb.append(tenantId == null ? 0 : tenantId).append('/')
          .append(kbId == null ? 0 : kbId).append('/')
          .append(today.format(Y)).append('/')
          .append(today.format(M)).append('/')
          .append(today.format(D)).append('/')
          .append(hash8).append('-').append(safe);
        return sb.toString();
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "untitled";
        String s = name.trim();
        // 去掉路径分隔符与控制字符；保留中英文与常见标点
        s = s.replaceAll("[\\\\/\\u0000-\\u001F]", "_");
        // 限长，避免 key 超过 1024 字节（MinIO 限制）
        if (s.length() > 200) {
            int dot = s.lastIndexOf('.');
            String ext = (dot > 0 && dot > s.length() - 12) ? s.substring(dot) : "";
            s = s.substring(0, 200 - ext.length()) + ext;
        }
        return s;
    }
}
