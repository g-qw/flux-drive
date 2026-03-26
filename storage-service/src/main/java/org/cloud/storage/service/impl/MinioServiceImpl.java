package org.cloud.storage.service.impl;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.cloud.storage.config.minio.MinioProperties;
import org.cloud.storage.service.MinioService;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {
    private final MinioProperties minioProperties;
    private final MinioClient minioClient;
    private final RedissonClient redissonClient;

    private static final String CACHE_PREFIX = "presigned:";
    private static final double REFRESH_THRESHOLD = 0.2;

    @Override
    public String getPresignedUrl(String bucket, String object, Method method, int expirySeconds) {
        String cacheKey = buildCacheKey(CACHE_PREFIX, bucket, object, method);
        RBucket<String> bucketCache = redissonClient.getBucket(cacheKey);

        String cachedUrl = bucketCache.get();
        if (cachedUrl != null && !isNearExpiry(bucketCache, expirySeconds)) {
            log.debug("Cache hit: {}", cacheKey);
            return cachedUrl;
        }

        // 使用外部 MinIO Client 生成预签名 URL
        String presignedUrl = generateExternalPresignedUrl(bucket, object, method, expirySeconds);

        long cacheTtl = (long) (expirySeconds * (1 - REFRESH_THRESHOLD));
        bucketCache.set(presignedUrl, Duration.ofSeconds(cacheTtl));

        log.debug("Generated presigned url: url={}, cacheKey={}, cacheTTL={}s", presignedUrl, cacheKey, cacheTtl);
        return presignedUrl;
    }

    /**
     * 生成外部访问的预签名 URL
     * 使用 externalMinioClient，其 endpoint 配置为 externalUrl
     */
    private String generateExternalPresignedUrl(String bucket, String object, Method method, int expirySeconds) {
        try {
            String internalUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(method)
                            .bucket(bucket)
                            .object(object)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );

            return internalUrl.replace(minioProperties.getEndpoint(), minioProperties.getServerUrl());
        } catch (Exception e) {
            log.error("Failed to generate external presigned URL for {}/{}: {}", bucket, object, e.getMessage());
            throw new RuntimeException(e);
        }
    }


    private boolean isNearExpiry(RBucket<String> bucket, int expirySeconds) {
        long remainTtl = bucket.remainTimeToLive();
        if (remainTtl <= 0) return true;

        long threshold = (long) (expirySeconds * 1000 * REFRESH_THRESHOLD);
        return remainTtl < threshold;
    }

    private String buildCacheKey(String prefix, String bucket, String object, Method method) {
        String composite = bucket + ":" + object + ":" + method.name();
        return prefix + DigestUtils.md5Hex(composite);
    }
}