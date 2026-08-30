package com.dearjolly.server.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "feedbackExecutor")
    @Profile("!test")
    public Executor feedbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("feedback-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskScheduler feedbackRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("feedback-retry-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "feedbackExecutor")
    @Profile("test")
    public Executor testFeedbackExecutor() {
        return new SyncTaskExecutor();
    }
}
