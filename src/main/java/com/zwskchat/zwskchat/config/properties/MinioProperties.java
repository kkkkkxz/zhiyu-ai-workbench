package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO / S3 兼容对象存储配置。
 *
 * <p>启用条件：{@link #enabled} 为 true 且 {@link #endpoint} 非空。
 * 关闭后所有上传走 inline 路径（content 直接进消息体），便于本机开发不依赖对象存储。</p>
 *
 * <p>对象 Key 命名规则（默认）：{@code {keyPrefix}/{tenantId}/{kbId}/{yyyy}/{MM}/{dd}/{hash}-{fileName}}。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "zwsk.storage.minio")
public class MinioProperties {

    /** 是否启用 MinIO 集成。false 时所有写文件路径回退为 inline。 */
    private boolean enabled = false;

    /** 形如 http://127.0.0.1:9000 */
    private String endpoint = "http://127.0.0.1:9000";

    /** 访问密钥（也叫 username / accessKey） */
    private String accessKey = "";

    /** 访问密钥密码（也叫 password / secretKey） */
    private String secretKey = "";

    /** 业务桶；不存在时启动期不立即建，首次写入时 lazy 创建。 */
    private String bucket = "zwsk-kb";

    /** 桶内对象 Key 的统一前缀，便于多业务共桶（保留为空表示无前缀）。 */
    private String keyPrefix = "kb";

    /** 单文件上限（字节）；同时受 spring.servlet.multipart.max-file-size 限制。 */
    private long maxFileSize = 50L * 1024 * 1024;

    /** 预签名下载 URL 默认有效期（秒）。 */
    private int presignedExpirySeconds = 600;
}
