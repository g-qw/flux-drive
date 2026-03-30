package org.cloud.message.constant;

/**
 * 交换机常量定义
 */
public final class ExchangeConstants {

    private ExchangeConstants() {}

    // 文件服务交换机
    public static final String FILE_EXCHANGE = "file.exchange";

    // 死信交换机（用于处理失败消息）
    public static final String FILE_DLX_EXCHANGE = "file.dlx.exchange";
}
