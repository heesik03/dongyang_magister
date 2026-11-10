package com.magister.backend.domain.scene;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class SceneBoardExecutionConfig {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler sceneBoardScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scene-board-worker-");
        return scheduler;
    }
}
