package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.HistoryFindingDecision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HistoryFindingDecisionRepository
        extends JpaRepository<HistoryFindingDecision, Long> {
    /** 특정 분석 작업의 역사 고증 항목 하나에 저장된 작가 판단을 조회한다. */
    Optional<HistoryFindingDecision> findByJob_IdAndFindingIndex(String jobId, int index);

    /** 특정 분석 작업에 저장된 모든 역사 고증 판단을 항목 순서대로 일괄 조회한다. */
    List<HistoryFindingDecision> findByJob_IdOrderByFindingIndexAsc(String jobId);

    /** 같은 작품의 이전 작업에 기록된 역사 고증 판단을 최신 작업 순으로 일괄 조회한다. */
    List<HistoryFindingDecision>
            findByJob_Work_IdAndClaimKeyInAndJob_CreatedAtBeforeOrderByJob_CreatedAtDescIdDesc(
                    Long workId, Collection<String> claimKeys, Instant before);
}
