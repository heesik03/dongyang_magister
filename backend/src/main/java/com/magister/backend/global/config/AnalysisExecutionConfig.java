package com.magister.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AnalysisExecutionProperties.class)
public class AnalysisExecutionConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor analysisExecutor(AnalysisExecutionProperties properties) {
        return new ThreadPoolExecutor(
                properties.getThreads(),
                properties.getThreads(),
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                Thread.ofPlatform().name("analysis-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
