package org.cloud.storage.service;

import io.minio.http.Method;

public interface MinioService {
    String getInternalPresignedUrl(String bucket, String object, Method method, int expirySeconds);

    String getExternalPresignedUrl(String bucket, String object, Method method, int expirySeconds);
}
