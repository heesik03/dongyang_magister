package com.magister.backend.domain.timeline.repository;

import java.time.Instant;

/** 폴링·이력에서 원고 스냅샷과 동의 요청 본문을 읽지 않는 투영이다. */
public interface ExtractionView {
    String getId();

    String getStatus();

    Long getVersion();

    Instant getCreatedAt();

    String getError();

    String getResult();
}
