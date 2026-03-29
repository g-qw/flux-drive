package org.cloud.storage.config.minio;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioInitializer implements ApplicationRunner {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /** 公开读策略模板（允许匿名 GetObject，其他操作需认证） */
    private static final String PUBLIC_READ_POLICY_TEMPLATE = """
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Sid": "PublicReadGetObject",
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": "s3:GetObject",
                    "Resource": "arn:aws:s3:::%s/*"
                }
            ]
        }
        """;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting MinIO initialization...");

        try {
            // 私有桶：文件存储
            initializePrivateBucket(minioProperties.getStorageBucket());

            // 私有桶：媒体预览
            initializePrivateBucket(minioProperties.getMediaPreviewBucket());

            // 公开读桶：头像（允许公开访问，但上传需认证）
            initializePublicReadBucket(minioProperties.getAvatarBucket());

            log.info("MinIO initialization completed successfully");
        } catch (Exception e) {
            log.error("MinIO initialization failed", e);
            throw new RuntimeException("MinIO initialization failed", e);
        }
    }

    /**
     * 初始化私有存储桶
     */
    private void initializePrivateBucket(String bucketName) throws Exception {

        // 创建桶（如果不存在）
        boolean newCreated = createBucketIfNotExists(bucketName);

        if(newCreated) {
            // 清除任何现有策略，确保完全私有
            minioClient.deleteBucketPolicy(
                    DeleteBucketPolicyArgs.builder().bucket(bucketName).build()
            );

            log.info("Initialized private bucket: {}", bucketName);
        }
    }

    /**
     * 初始化公开读存储桶（公开读，私有写）
     */
    private void initializePublicReadBucket(String bucketName) throws Exception {
        // 创建桶（如果不存在）
        boolean newCreated = createBucketIfNotExists(bucketName);

        if(newCreated) {
            // 设置公开读策略
            String policyJson = String.format(PUBLIC_READ_POLICY_TEMPLATE, bucketName);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policyJson)
                            .build()
            );

            log.info("Initialized public-read bucket: {}", bucketName);
        }
    }

    /**
     * 创建存储桶（如果不存在）
     *
     * @return 是否创建存储桶，如果已存在则返回 false
     */
    private boolean createBucketIfNotExists(String bucketName) throws Exception {

        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("Created bucket: {}", bucketName);

            return true;
        }

        return false;
    }
}