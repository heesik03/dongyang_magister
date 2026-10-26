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

@Getter
@Setter
@Validated
@ConfigurationProperties("auth.rate-limit")
public class AuthRateLimitProperties {
    @Min(1) private int loginAttempts = 20;
    @Min(1) private int signupAttempts = 20;
    @Min(1) private int emailChecks = 60;
    @Min(1) private int maxTrackedClients = 10_000;
    @Min(1) @Max(64) private int concurrentHashes = 4;
    @NotNull private Duration window = Duration.ofMinutes(1);

    @AssertTrue(message = "window must be between 1 second and 1 hour")
    public boolean isWindowValid() {
        return window != null && window.compareTo(Duration.ofSeconds(1)) >= 0
                && window.compareTo(Duration.ofHours(1)) <= 0;
    }
}
