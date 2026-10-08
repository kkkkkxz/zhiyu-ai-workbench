package com.zwskchat.zwskchat.storage;

import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 实现的 {@link ObjectStorage}。
 *
 * <p>启动期只初始化 SDK 客户端不连后端，避免 MinIO 还没起就拖垮 Spring 上下文；
 * 桶的存在性检查放到首次 upload/delete 时 lazy 触发，结果用 {@link #bucketEnsured}
 * 缓存避免重复调用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorage implements ObjectStorage {

    private final MinioProperties props;
    private MinioClient client;
    private final AtomicBoolean bucketEnsured = new AtomicBoolean(false);

    @PostConstruct
    void init() {
        if (!props.isEnabled()) {
            log.info("[Storage] MinIO disabled by config (zwsk.storage.minio.enabled=false)");
            return;
        }
        this.client = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        // 给 SDK 一个相对宽松的超时（连接/读取/写入），避免大文件被默认 5min 限制
        this.client.setTimeout(
                TimeUnit.SECONDS.toMillis(15),
                TimeUnit.MINUTES.toMillis(5),
                TimeUnit.MINUTES.toMillis(10));
        log.info("[Storage] MinIO client ready endpoint={} bucket={}", props.getEndpoint(), props.getBucket());
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && client != null;
    }

    private void ensureBucket() {
        if (bucketEnsured.get()) return;
        if (!isEnabled()) {
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "对象存储未启用");
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("[Storage] bucket created: {}", props.getBucket());
            }
            bucketEnsured.set(true);
        } catch (Exception e) {
            log.error("[Storage] ensureBucket failed", e);
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "MinIO 不可用：" + e.getMessage());
        }
    }

    @Override
    public String upload(String objectKey, InputStream in, long size, String contentType) {
        ensureBucket();
        try {
            String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(type)
                    .build());
            return objectKey;
        } catch (Exception e) {
            log.error("[Storage] upload failed key={}", objectKey, e);
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "MinIO upload 失败：" + e.getMessage());
        }
    }

    @Override
    public InputStream openStream(String objectKey) {
        ensureBucket();
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("[Storage] openStream failed key={}", objectKey, e);
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "MinIO get 失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        if (!isEnabled()) return;
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException ere) {
            // 对象不存在等同删除成功，幂等
            String code = ere.errorResponse() == null ? "" : ere.errorResponse().code();
            if ("NoSuchKey".equalsIgnoreCase(code) || "NoSuchObject".equalsIgnoreCase(code)) return;
            log.warn("[Storage] delete err code={} key={}", code, objectKey);
        } catch (Exception e) {
            log.warn("[Storage] delete failed key={} : {}", objectKey, e.getMessage());
        }
    }

    @Override
    public String presignedDownloadUrl(String objectKey, int expirySeconds) {
        ensureBucket();
        try {
            int exp = expirySeconds > 0 ? expirySeconds : props.getPresignedExpirySeconds();
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .expiry(exp, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("[Storage] presigned url failed key={}", objectKey, e);
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "MinIO 预签名失败：" + e.getMessage());
        }
    }
}
