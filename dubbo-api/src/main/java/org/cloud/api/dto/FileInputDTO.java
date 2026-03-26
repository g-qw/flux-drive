package org.cloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInputDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String directoryId;

    private String bucket;

    private String storageKey;

    private String name;

    private String mimeType;

    private Long size;

    private String md5;
}
