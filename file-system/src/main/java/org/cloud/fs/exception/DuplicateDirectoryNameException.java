package org.cloud.fs.exception;

import lombok.Getter;

@Getter
public class DuplicateDirectoryNameException extends RuntimeException {
    private String name;

    public DuplicateDirectoryNameException() {
    }

    public DuplicateDirectoryNameException(String name) {
        this.name = name;
    }
}
