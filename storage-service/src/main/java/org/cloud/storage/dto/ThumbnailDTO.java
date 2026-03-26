package org.cloud.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ThumbnailDTO {
    private byte[] bytes;
    private int width;
    private int height;
}
