package org.cloud.storage.schedule;

import com.google.common.collect.Lists;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloud.storage.config.minio.MinioProperties;
import org.cloud.storage.dto.MediaCoverInput;
import org.cloud.storage.dto.ThumbnailDTO;
import org.cloud.storage.dto.enums.TaskType;
import org.cloud.storage.dto.enums.ThumbnailFormat;
import org.cloud.storage.entity.FileProcessingTask;
import org.cloud.storage.repository.FileProcessingTaskRepository;
import org.cloud.storage.repository.MediaCoverRepository;
import org.cloud.storage.util.StorageKeyGenerator;
import org.cloud.storage.util.ThumbnailGenerator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailScheduler {
    private final ThumbnailGenerator thumbnailGenerator;
    private final MediaCoverRepository mediaCoverRepository;
    private final FileProcessingTaskRepository fileProcessingTaskRepository;
    private final MinioProperties minioProperties;
    private final MinioClient minioClient;
    private final ThreadPoolTaskExecutor taskExecutor;

    private static final ThumbnailFormat DEFAULT_THUMBNAIL_FORMAT = ThumbnailFormat.WEBP;
    private static final int CONCURRENCY = 4;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    @Scheduled(initialDelay = 7, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void batchProcessThumbnailTask() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        try {
            List<FileProcessingTask> tasks = fileProcessingTaskRepository.listUnprocessedTaskByTaskType(TaskType.THUMBNAIL);

            if (CollectionUtils.isEmpty(tasks)) {
                processing.set(false);
                return;
            }

            log.info("Processing {} thumbnail tasks...", tasks.size());

            List<List<FileProcessingTask>> batches = Lists.partition(tasks, CONCURRENCY);

            for (List<FileProcessingTask> batch : batches) {
                CountDownLatch latch = new CountDownLatch(batch.size());
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (FileProcessingTask task : batch) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            processThumbnailTask(task);
                        } catch (Exception e) {
                            log.error("Failed to process thumbnail task: taskId={}, fileId={}, error={}", task.id(), task.fileId(), e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    }, taskExecutor);

                    futures.add(future);
                }

                try {
                    latch.await(5, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Batch processing interrupted", e);
                    break;
                }

                // 检查是否有异常
                futures.forEach(future -> {
                    try {
                        future.get(1, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Task execution error", e);
                    }
                });
            }
        } finally {
            processing.set(false);
        }
    }

    /**
     * 处理单个缩略图任务
     */
    private void processThumbnailTask(FileProcessingTask task) throws Exception {
        // 从MinIO获取原始文件
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getStorageBucket())
                        .object(task.storageKey())
                        .build()
        )) {
            // 生成缩略图
            ThumbnailDTO dto = thumbnailGenerator.generate(inputStream, 256, 256, DEFAULT_THUMBNAIL_FORMAT);

            byte[] thumbnailBytes = dto.getBytes();
            int width = dto.getWidth();
            int height = dto.getHeight();

            // 上传到预览桶
            String previewKey = StorageKeyGenerator.generateKey(task.userId(), task.fileId());
            uploadThumbnail(thumbnailBytes, previewKey);

            // 更新任务状态为完成
            fileProcessingTaskRepository.markAsProcessed(task.id());

            // 设置封面
            mediaCoverRepository.create(
                new MediaCoverInput.Builder()
                    .fileId(task.fileId())
                    .userId(task.userId())
                    .md5(task.md5())
                    .bucket(minioProperties.getMediaPreviewBucket())
                    .storageKey(previewKey)
                    .size((long) thumbnailBytes.length)
                    .width(width)
                    .height(height)
                    .build()
            );

            log.info("Thumbnail task processed: taskId={}, fileId={}, previewKey={}", task.id(), task.fileId(), previewKey);
        }
    }

    /**
     * 上传缩略图到MinIO
     */
    private void uploadThumbnail(byte[] thumbnailBytes, String previewKey) throws Exception {
        try (InputStream uploadStream = new ByteArrayInputStream(thumbnailBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getMediaPreviewBucket())
                            .object(previewKey)
                            .stream(uploadStream, thumbnailBytes.length, -1)
                            .contentType(DEFAULT_THUMBNAIL_FORMAT.getMimeType())
                            .build()
            );
        }
    }
}
