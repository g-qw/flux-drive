package org.cloud.storage.exception;

public class FileRangeDownloadException extends RuntimeException {
    public FileRangeDownloadException(String message) {
        super(message);
    }
}
