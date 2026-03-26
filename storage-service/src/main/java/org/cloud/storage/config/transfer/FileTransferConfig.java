package org.cloud.storage.config.transfer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "transfer")
public class FileTransferConfig {
    /** 多文件上传配置 */
    private MultiFileUploadConfig multiFileConfig = new MultiFileUploadConfig();

    /** 分块上传配置 */
    private ChunkedUploadConfig chunkedUploadConfig = new ChunkedUploadConfig();

    /** 文件下载配置 */
    private DownloadConfig downloadConfig = new DownloadConfig();

    @Data
    public static class MultiFileUploadConfig {
        /**
         * 内存直传阈值（字节）
         */
        private int memoryThreshold = 5 * 1024 * 1024;

        /**
         * 文件流式上传阈值（字节）
         */
        private int streamThreshold = 100 * 1024 * 1024;

        /**
         * 流式上传的缓冲区大小
         */
        private int streamBufferSize = 16 * 1024;

        /**
         * 多文件上传时，最大并发处理文件数
         */
        private int maxConcurrency = Runtime.getRuntime().availableProcessors() * 2;

        /**
         * 背压缓冲区大小，突发流量缓冲队列容量，用于平滑处理流量峰值
         */
        private int backpressureBufferSize = maxConcurrency * 4;

        /**
         * 多文件上传中，单个文件处理超时（分钟），防止单个文件处理 hang 住导致资源泄漏
         */
        private long fileTimeoutMinutes = 5;

        /**
         * 多文件上传中，一次批量请求的总超时（分钟），整个批量上传请求的最大处理时间
         */
        private long batchTimeoutMinutes = 10;

        /**
         * 流式上传预取数量，背压机制下的预取缓冲区数量，平衡吞吐量和内存占用
         */
        private int streamPrefetch = 4;

        /**
         * 缓冲区溢出策略, 背压缓冲区满时的处理策略：<br/>
         * - DROP_OLDEST: 丢弃最旧的任务（优先保证新任务）<br/>
         * - DROP_LATEST: 丢弃最新的任务（优先保证旧任务）<br/>
         */
        private String overflowStrategy = "DROP_OLDEST";
    }

    @Data
    public static class ChunkedUploadConfig {
        /**
         * 分片上传中，分片大小限制（字节）
         */
        private int maxChunkSizeBytes = 10 * 1024 * 1024;

        /**
         * 单分片上传超时（分钟）
         */
        private long chunkTimeoutMinutes = 1;
    }

    @Data
    public static class DownloadConfig {
        /**
         * 流式下载缓冲区大小（字节），默认 8KB
         */
        private int streamBufferSizeBytes = 16384;

        /**
         * 分片下载大小限制（字节）, 单个下载分片的最大大小，用于大文件分片下载
         */
        private long maxChunkSizeBytes = 10 * 1024 * 1024;

        /**
         * ZIP批量打包大小限制（字节）
         */
        private long maxZipBatchSizeBytes = 1024 * 1024 * 1024;

        /**
         * ZIP打包超时（分钟）
         */
        private long zipPackagingTimeoutMinutes = 10;
    }
}
