package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.vector.qdrant")
public class QdrantProperties {
    private String host = "127.0.0.1";
    private int port = 6333;
    private String apiKey;
    /** TCP 连接超时秒数。 */
    private int connectTimeoutSeconds = 3;
    /** 普通管理/写入请求超时秒数。 */
    private int requestTimeoutSeconds = 10;
    /** 检索请求超时秒数；语音场景需要快速降级，避免卡住实时对话。 */
    private int searchTimeoutSeconds = 4;
    /** 默认 Collection 命名前缀，租户级隔离：{prefix}_{tenantId} */
    private String collectionPrefix = "zwsk_kb";
    /** 距离度量：Cosine / Dot / Euclid */
    private String distance = "Cosine";
}
