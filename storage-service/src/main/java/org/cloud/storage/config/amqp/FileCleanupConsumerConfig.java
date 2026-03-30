package org.cloud.storage.config.amqp;

import org.cloud.message.constant.ExchangeConstants;
import org.cloud.message.constant.QueueConstants;
import org.cloud.message.constant.RoutingKeyConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileCleanupConsumerConfig {
    /**
     * 文件交换机
     */
    @Bean
    public DirectExchange fileExchange() {
        return ExchangeBuilder
                .directExchange(ExchangeConstants.FILE_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 文件删除队列
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
}
