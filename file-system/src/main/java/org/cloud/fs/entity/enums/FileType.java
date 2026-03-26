package org.cloud.fs.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum FileType {
    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video"),
    DOCUMENT("document");

    private final String value;

    private static final Map<String, FileType> VALUE_MAP = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.value.toLowerCase(),
                    type -> type
            ));

    public static FileType from(String value) {
        if (value == null) {
            return null;
        }
        FileType type = VALUE_MAP.get(value.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Invalid file type: " + value);
        }
        return type;
    }
}
