package com.magister.backend.domain.timeline.repository;

import com.magister.backend.domain.timeline.entity.TimelineEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {
    List<TimelineEvent> findByWork_IdOrderBySequenceAscIdAsc(Long workId);

    Optional<TimelineEvent> findByWork_IdAndEventKey(Long workId, String eventKey);

    long countByWork_Id(Long workId);
}
