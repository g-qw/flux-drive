package org.cloud.message.dto;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * 文件删除消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileCleanupMessage implements Serializable {

    private UUID id;

    private String bucket;

    private String storageKey;

    private Long timestamp;
}