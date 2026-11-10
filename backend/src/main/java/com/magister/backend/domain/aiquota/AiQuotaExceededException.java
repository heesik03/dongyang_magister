package com.magister.backend.domain.aiquota;

import java.time.Instant;

public class AiQuotaExceededException extends RuntimeException {
    private final Instant resetAt;

    public AiQuotaExceededException(Instant resetAt) {
        super("오늘 사용할 수 있는 AI 작업 횟수를 모두 사용했습니다.");
        this.resetAt = resetAt;
    }

    public Instant resetAt() {
        return resetAt;
    }
}
