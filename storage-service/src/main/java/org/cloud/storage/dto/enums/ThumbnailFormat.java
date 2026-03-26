package org.cloud.storage.dto.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ThumbnailFormat {
    WEBP("webp", "image/webp"),
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png");

    private final String extension;
    private final String mimeType;
}
