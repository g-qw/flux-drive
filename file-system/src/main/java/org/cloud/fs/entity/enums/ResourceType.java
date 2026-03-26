package org.cloud.fs.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum ResourceType {
    FILE("file"),
    DIRECTORY("dir"),
    ;

    private final String value;

    private static final Map<String, ResourceType> VALUE_MAP = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.value.toLowerCase(),
                    type -> type
            ));

    public static ResourceType from(String value) {
        if (value == null) {
            return null;
        }
        ResourceType type = VALUE_MAP.get(value.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Invalid file type: " + value);
        }
        return type;
    }
}
