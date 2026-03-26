package org.cloud.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadMetadata implements Serializable {
    private String directoryId;
    private String storageKey;
    private String filename;
    private String contentType;
    private Long fileSize;
    private Long createdAt;
}
