package org.cloud.storage.listener;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloud.message.dto.FileCleanupMessage;
import org.cloud.message.constant.QueueConstants;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(
        queues = QueueConstants.FILE_DELETE_QUEUE,
        containerFactory = "rabbitListenerContainerFactory"
)
public class FileCleanupListener {
    private final MinioClient minioClient;

    @RabbitHandler
    public void handleFileCleanup(@Payload FileCleanupMessage message) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(message.getBucket())
                            .object(message.getStorageKey())
                            .build()
            );
        } catch (Exception ignored) {

        }
    }
}