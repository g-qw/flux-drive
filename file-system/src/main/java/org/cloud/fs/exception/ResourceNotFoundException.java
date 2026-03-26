package org.cloud.fs.exception;

import lombok.Getter;

@Getter
public class ResourceNotFoundException extends RuntimeException {
    private final String path;

    public ResourceNotFoundException(String path) {
        this.path = path;
    }
}
