package com.magister.backend.domain.aiquota;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 저장소와 무관하게 한국 시간 기준 일별 집계 기간을 계산한다. */
public final class AiQuotaPolicy {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private AiQuotaPolicy() {}

    public enum Feature {
        CHARACTER_IMAGE
    }

    public record Period(LocalDate start, Instant resetAt) {}

    public static Period periodAt(Instant now) {
        LocalDate start = now.atZone(SEOUL).toLocalDate();
        return new Period(start, start.plusDays(1).atStartOfDay(SEOUL).toInstant());
    }
}
