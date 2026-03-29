package org.cloud.storage.schedule;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloud.storage.config.minio.MinioProperties;
import org.cloud.storage.dto.MediaCoverInput;
import org.cloud.storage.dto.enums.TaskType;
import org.cloud.storage.entity.FileProcessingTask;
import org.cloud.storage.repository.FileProcessingTaskRepository;
import org.cloud.storage.repository.MediaCoverRepository;
import org.cloud.storage.util.GifGenerator;
import org.cloud.storage.util.StorageKeyGenerator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class GifScheduler {
    private final GifGenerator gifGenerator;
    private final FileProcessingTaskRepository fileProcessingTaskRepository;
    private final MediaCoverRepository mediaCoverRepository;
    private final MinioProperties minioProperties;
    private final MinioClient minioClient;

    private final AtomicBoolean processing = new AtomicBoolean(false);
    private static Path TEMP_DIR = Paths.get("tmp");

    static {
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            // 创建失败，使用系统默认临时目录
            TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));
        }
    }

    @Scheduled(initialDelay = 13, fixedDelay = 300, timeUnit = TimeUnit.SECONDS)
    public void batchProcessGifTask() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        try {
            List<FileProcessingTask> tasks = fileProcessingTaskRepository.listUnprocessedTaskByTaskType(TaskType.GIF);

            if (CollectionUtils.isEmpty(tasks)) {
                processing.set(false);
                return;
            }

            log.info("Processing {} GIF tasks...", tasks.size());

            for(FileProcessingTask task: tasks) {
                try {
                    processSingleGifTask(task);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        } finally {
            processing.set(false);
        }
    }

    private void processSingleGifTask(FileProcessingTask task) throws Exception {
        String bucket = minioProperties.getStorageBucket();
        String objectKey = task.storageKey();

        // 生成预签名URL
        String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(300) // 有效时长 5 分钟
                        .build()
        );

        // 使用 FFmpeg 分析
        GifGenerator.VideoInfo videoInfo = gifGenerator.extractVideoInfo(url);
        if(videoInfo == null) {
            return;
        }

        // 获取完整视频流生成GIF
        byte[] gifBytes;
        Path videoFile = TEMP_DIR.resolve(task.fileId() + ".tmp");
        try (GetObjectResponse video = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        )) {
            Files.copy(video, videoFile, StandardCopyOption.REPLACE_EXISTING);
            gifBytes = gifGenerator.generatePreviewGif(videoFile, videoInfo);
        } finally {
            Files.deleteIfExists(videoFile);
        }

        String previewKey = StorageKeyGenerator.generateKey(task.userId(), task.fileId());
        try (ByteArrayInputStream gifStream = new ByteArrayInputStream(gifBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getMediaPreviewBucket())
                            .object(previewKey)
                            .stream(gifStream, gifBytes.length, -1)
                            .contentType("image/gif")
                            .build()
            );
        }

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
                        .size((long) gifBytes.length)
                        .width(videoInfo.width())
                        .height(videoInfo.height())
                        .build()
        );

        log.info("Processed GIF task: taskId={}, fileId={}, previewKey={}", task.id(), task.fileId(), previewKey);
    }
}
