package org.cloud.fs.exception;

import lombok.Getter;

@Getter
public class InvalidDirectoryNameException extends RuntimeException {
    private final boolean lengthExceeded;
    private final String illegalChars;

    public InvalidDirectoryNameException(boolean lengthExceeded, String illegalChars) {
        this.lengthExceeded = lengthExceeded;
        this.illegalChars = illegalChars;
    }
}