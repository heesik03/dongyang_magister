package com.magister.backend.domain.work.repository;

/** 보관함에 필요한 요약만 조회하며 원고·설정 본문은 가져오지 않는다. */
public interface WorkSummary {
    Long getWorkId();
    Long getChapterCount();
    Long getSettingCount();
    String getLatestChapterId();
    String getLatestChapterTitle();
}
