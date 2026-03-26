package org.cloud.user.config.thread;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "shared-task.pool")
public class SharedTaskPoolProperties {
    private int corePoolSize = Runtime.getRuntime().availableProcessors();
    private int maxPoolSize  = Runtime.getRuntime().availableProcessors() * 2;
    private int queueCapacity = 100;
    private int keepAliveSeconds = 60;
    private String threadNamePrefix = "shared-task-";
    private int scheduledPoolSize = 16;
}