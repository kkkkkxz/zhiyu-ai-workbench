package com.zwskchat.zwskchat.storage;

import java.io.InputStream;

/**
 * 对象存储 SPI。当前仅 MinIO 实现，后续可扩展 OSS / S3 / 七牛云 等。
 *
 * <p>所有方法在依赖不可用时抛 {@code BusinessException(DEPENDENCY_UNAVAILABLE)}，
 * 便于在 controller / service 层统一返回 5xx 响应而非裸 IOException。</p>
 */
public interface ObjectStorage {

    /** MinIO 等后端是否启用。false 时调用方应走 inline 兜底路径。 */
    boolean isEnabled();

    /**
     * 上传字节流到指定 key（覆盖语义；客户端应保证 key 唯一）。
     *
     * @param objectKey   对象在桶内的完整 key（含前缀）
     * @param in          字节流；调用方负责关闭（实现内部不会关）
     * @param size        字节数；用于服务端 multipart 切分
     * @param contentType MIME 类型；可为 null（实现给默认 application/octet-stream）
     * @return 实际写入的 objectKey（与入参相同；保留方法签名以便未来生成 key）
     */
    String upload(String objectKey, InputStream in, long size, String contentType);

    /**
     * 打开对象的输入流。
     * 调用方必须 close。
     */
    InputStream openStream(String objectKey);

    /** 不存在时静默返回。 */
    void delete(String objectKey);

    /**
     * 生成一个临时下载 URL（presigned GET）。
     *
     * @param objectKey      对象 key
     * @param expirySeconds  有效期（秒）
     */
    String presignedDownloadUrl(String objectKey, int expirySeconds);
}
