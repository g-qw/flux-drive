package org.cloud.fs.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum ShareStatus {
    ACTIVE("active"),
    DISABLED("disabled"),
    EXPIRED("expired"),
    REVOKED("revoked"),
    ;

    private final String value;

    private static final Map<String, ShareStatus> VALUE_MAP = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.value.toLowerCase(),
                    type -> type
            ));

    public static ShareStatus from(String value) {
        if (value == null) {
            return null;
        }
        ShareStatus status = VALUE_MAP.get(value.toLowerCase());
        if (status == null) {
            throw new IllegalArgumentException("Invalid file type: " + value);
        }
        return status;
    }

}
