package com.magister.backend.global.gemini;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("gemini")
public record GeminiProperties(
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotNull Duration timeout) {

    @AssertTrue(message = "timeout must be between 1ms and 2147483647ms")
    public boolean isTimeoutValid() {
        return timeout != null
                && timeout.compareTo(Duration.ofMillis(1)) >= 0
                && timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0;
    }

    @Override
    public String toString() {
        return "GeminiProperties[apiKey=REDACTED, model=" + model + ", timeout=" + timeout + "]";
    }
}
