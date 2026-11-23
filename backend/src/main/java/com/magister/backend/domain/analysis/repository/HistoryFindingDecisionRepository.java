package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.HistoryFindingDecision;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryFindingDecisionRepository extends JpaRepository<HistoryFindingDecision, Long> {
    Optional<HistoryFindingDecision> findByJob_IdAndFindingIndex(String jobId, int index);

    Optional<HistoryFindingDecision> findFirstByJob_Work_IdAndClaimKeyAndJob_CreatedAtBeforeOrderByJob_CreatedAtDescIdDesc(
            Long workId, String claimKey, Instant before);
}
