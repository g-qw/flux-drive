package org.cloud.storage.service;

import io.minio.http.Method;

public interface MinioService {
    String getPresignedUrl(String bucket, String object, Method method, int expirySeconds);
}
