package org.cloud.fs.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum ShareType {
    PUBLIC("public"), // 公开
    PRIVATE("private"), // 白名单
    ;

    private final String value;

    private static final Map<String, ShareType> VALUE_MAP = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.value.toLowerCase(),
                    type -> type
            ));

    public static ShareType from(String value) {
        if (value == null) {
            return null;
        }
        ShareType type = VALUE_MAP.get(value.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Invalid file type: " + value);
        }
        return type;
    }
}
