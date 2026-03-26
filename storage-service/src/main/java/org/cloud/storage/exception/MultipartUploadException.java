package org.cloud.storage.exception;

public class MultipartUploadException extends RuntimeException{
    public MultipartUploadException(String message) {
        super(message);
    }
}
