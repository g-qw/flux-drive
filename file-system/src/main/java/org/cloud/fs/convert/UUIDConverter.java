package org.cloud.fs.convert;

import org.mapstruct.*;

import java.util.UUID;

public class UUIDConverter {
    @Named("uuidToString")
    public static String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("stringToUUID")
    public static UUID stringToUUID(String uuid) {
        return uuid != null && !uuid.isEmpty() ? UUID.fromString(uuid) : null;
    }
}