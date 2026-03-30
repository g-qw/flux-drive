package org.cloud.fs.config.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloud.message.constant.ExchangeConstants;
import org.cloud.message.constant.QueueConstants;
import org.cloud.message.constant.RoutingKeyConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileCleanupProducerConfig {
    // ==================== 普通交换机和队列 ====================

    /**
     * 创建文件服务直连交换机
     */
    @Bean
    public DirectExchange fileExchange() {
        return ExchangeBuilder
                .directExchange(ExchangeConstants.FILE_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 创建删除文件队列（带死信交换机参数）
     * 当消息被拒绝、过期或达到最大重试次数时，会转发到死信交换机
     */
    @Bean
    public Queue fileDeleteQueue() {
        return QueueBuilder.durable(QueueConstants.FILE_DELETE_QUEUE)
                .withArgument("x-dead-letter-exchange", ExchangeConstants.FILE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RoutingKeyConstants.FILE_DELETE_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定删除队列到文件交换机
     */
    @Bean
    public Binding fileDeleteBinding() {
        return BindingBuilder.bind(fileDeleteQueue())
                .to(fileExchange())
                .with(RoutingKeyConstants.FILE_DELETE_ROUTING_KEY);
    }

    // ==================== 死信交换机和队列 ====================

    /**
     * 创建死信交换机（Direct类型）
     * 用于接收处理失败的消息
     */
    @Bean
    public DirectExchange fileDlxExchange() {
        return ExchangeBuilder
                .directExchange(ExchangeConstants.FILE_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 创建文件删除死信队列
     * 存储无法正常处理的消息，便于后续人工介入或重试
     */
    @Bean
    public Queue fileDeleteDlq() {
        return QueueBuilder.durable(QueueConstants.FILE_DELETE_DLQ).build();
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding fileDeleteDlqBinding() {
        return BindingBuilder.bind(fileDeleteDlq())
                .to(fileDlxExchange())
                .with(RoutingKeyConstants.FILE_DELETE_DLQ_ROUTING_KEY);
    }
}
