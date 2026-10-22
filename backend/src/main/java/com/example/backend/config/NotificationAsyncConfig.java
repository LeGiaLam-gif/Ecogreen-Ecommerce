package com.example.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;

/**
 * The ONLY place {@code @EnableAsync} is switched on (B10). Notifications run on their own small, bounded pool so a slow or dead
 * SMTP server can never use up the request threads. When the pool and its queue are full a notification is DROPPED and logged:
 * {@code CallerRunsPolicy} is deliberately not used, because it would run the send on the thread that handled the business
 * request, which is exactly what must never happen. Dropped notifications are not retried (best effort).
 */
@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    public static final String EXECUTOR = "notificationExecutor";

    private static final Logger log = LoggerFactory.getLogger(NotificationAsyncConfig.class);

    static final int CORE_POOL_SIZE = 2;
    static final int MAX_POOL_SIZE = 4;
    static final int QUEUE_CAPACITY = 200;

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setRejectedExecutionHandler(discardAndLog());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /** Drops the task and logs one line; never runs it and never throws into the submitting (business) thread. */
    static RejectedExecutionHandler discardAndLog() {
        return (task, pool) -> log.warn("Notification dropped: the executor is saturated (queue full)");
    }
}
