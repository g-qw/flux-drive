package org.cloud.fs.exception;

import lombok.Getter;

@Getter
public class AccessDeniedException extends RuntimeException{
    public AccessDeniedException() {
    }
}
