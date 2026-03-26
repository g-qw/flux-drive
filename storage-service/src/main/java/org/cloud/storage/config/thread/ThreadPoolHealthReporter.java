package org.cloud.storage.config.thread;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Component
@Slf4j
public class ThreadPoolHealthReporter {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ThreadPoolTaskScheduler taskScheduler;

    public ThreadPoolHealthReporter(@Qualifier("sharedTaskExecutor") ThreadPoolTaskExecutor taskExecutor, @Qualifier("sharedTaskScheduler") ThreadPoolTaskScheduler taskScheduler) {
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
    }

    /**
     * 每 5 分钟打印一次当前任务线程池和调度线程池的状态
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void threadPoolHealthSchedule() {
        taskExecutor.execute(
                this::printThreadPoolStatus
        );
    }

    /**
     * 打印当前任务线程池和调度线程池的状态
     */
    public void printThreadPoolStatus() {
        // TaskExecutor 数据
        ThreadPoolExecutor exec = taskExecutor.getThreadPoolExecutor();
        int core = exec.getCorePoolSize();
        int max  = exec.getMaximumPoolSize();
        int pool = exec.getPoolSize();
        int active = exec.getActiveCount();
        int queue = exec.getQueue().size();

        // TaskScheduler 数据
        ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) taskScheduler.getScheduledExecutor();
        int scheduledPool   = stpe.getPoolSize();
        int scheduledActive = stpe.getActiveCount();
        long queuedScheduledTask  = stpe.getTaskCount() - stpe.getCompletedTaskCount(); // 剩余任务

        // 组装输出
        String banner = """
            ┌─────────────────────────────┬─────┬─────┬─────┬─────┬─────┐
            │ Thread Pool                 │ core│ max │ cur │ act │queue│
            ├─────────────────────────────┼─────┼─────┼─────┼─────┼─────┤
            │ %-27s │%5d│%5d│%5d│%5d│%5d│
            ├─────────────────────────────┼─────┼─────┼─────┼─────┼─────┤
            │ %-27s │  —  │  —  │%5d│%5d│%5d│
            └─────────────────────────────┴─────┴─────┴─────┴─────┴─────┘
            """;
        log.info("\n{}", String.format(banner,
                "taskExecutor", core, max, pool, active, queue,
                "taskScheduler", scheduledPool, scheduledActive, queuedScheduledTask));
    }
}