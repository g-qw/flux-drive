package org.cloud.message.constant;

/**
 * 队列常量定义
 */
public final class QueueConstants {

    private QueueConstants() {}

    // 文件删除队列
    public static final String FILE_DELETE_QUEUE = "file.delete.queue";

    // 文件删除死信队列
    public static final String FILE_DELETE_DLQ = "file.delete.dlq";
}