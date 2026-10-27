package com.magister.backend.global.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.UUID;

@Getter
@Setter
@Validated
@ConfigurationProperties("analysis.execution")
public class AnalysisExecutionProperties {
    private final String instanceId = UUID.randomUUID().toString();

    @Min(1)
    @Max(10)
    private int threads = 2;

    @Min(1)
    @Max(100)
    private int queueCapacity = 8;

    @Min(1)
    @Max(100)
    private int maxActiveJobs = 10;

    @NotNull private Duration leaseDuration = Duration.ofMinutes(2);
    // 0은 자동 삭제 비활성이다. 운영자가 정하기 전에는 원고 스냅샷을 삭제하지 않는다.
    @NotNull private Duration retention = Duration.ZERO;

    @AssertTrue(
            message =
                    "leaseDuration must be at least 30 seconds and retention must not be negative")
    public boolean isDurationValid() {
        return leaseDuration != null
                && leaseDuration.compareTo(Duration.ofSeconds(30)) >= 0
                && retention != null
                && !retention.isNegative();
    }
}
