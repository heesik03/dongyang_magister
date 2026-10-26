package com.magister.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class AnalysisExecutionConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor analysisExecutor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8),
                Thread.ofPlatform().name("analysis-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
