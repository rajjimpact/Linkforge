package com.linkforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration.
 * Uses Java 21 Virtual Threads for high-throughput async tasks.
 * Analytics event processing runs on a separate dedicated thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Default async executor — uses Java 21 virtual threads.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        // Java 21 Virtual Threads — managed by JVM, very lightweight
        return command -> Thread.ofVirtual()
                .name("async-virtual-", 0)
                .start(command);
    }

    /**
     * Dedicated executor for analytics event processing.
     * Bounded pool to prevent resource exhaustion under high load.
     */
    @Bean(name = "analyticsExecutor")
    public Executor analyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("analytics-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            // On overflow, log and drop — analytics is best-effort
            System.err.println("[WARN] Analytics queue full, dropping event");
        });
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor for email sending.
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
