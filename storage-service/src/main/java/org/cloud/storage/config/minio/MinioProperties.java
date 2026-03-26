package org.cloud.storage.config.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 外部访问地址（生成URL给客户端用），例如: http://192.168.64.1
     */
    private String serverUrl;

    /**
     * MinIO Access Key ID（访问标识）
     */
    private String accessKey;

    /**
     * MinIO Secret Access Key（访问密钥，需保密）
     */
    private String secretKey;

    /**
     * 文件系统的存储桶
     */
    private String storageBucket = "default-storage";

    /**
     * 媒体预览的存储桶
     */
    private String MidiaPreviewBucket = "media-preview";

    /**
     * 头像的存储桶
     */
    private String AvatarBucket = "avatar";

    /**
     * 预签名URL默认过期时间（秒）
     */
    private int presignedExpiry =  24 * 60 * 60;

    public String getBaseUrl() {
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            return serverUrl.trim();
        }

        return endpoint;
    }
}
