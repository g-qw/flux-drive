package org.cloud.fs.exception;

import lombok.Getter;

@Getter
public class DuplicateFileNameException extends RuntimeException {
    private String name;

    public DuplicateFileNameException() {
    }

    public DuplicateFileNameException(String name) {
        this.name = name;
    }
}
