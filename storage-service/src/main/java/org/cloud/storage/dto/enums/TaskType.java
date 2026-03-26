package org.cloud.storage.dto.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum TaskType {
    THUMBNAIL("thumbnail"),
    GIF("gif");

    private final String code;

    private static final Map<String, TaskType> CODE_MAP = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.code.toLowerCase(),
                    type -> type
            ));

    public static TaskType from(String code) {
        if (code == null) {
            return null;
        }
        TaskType type = CODE_MAP.get(code.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Invalid file type: " + code);
        }
        return type;
    }
}
