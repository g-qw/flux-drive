package org.cloud.message.constant;

/**
 * 路由键常量定义
 */
public final class RoutingKeyConstants {

    private RoutingKeyConstants() {}

    // 文件删除
    public static final String FILE_DELETE_ROUTING_KEY = "file.delete";

    // 死信路由键
    public static final String FILE_DELETE_DLQ_ROUTING_KEY = "file.delete.dlq";
}