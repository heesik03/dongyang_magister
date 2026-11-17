package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.timeline.entity.TimelineEvent;
import com.magister.backend.domain.timeline.repository.TimelineEventRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.domain.workspace.service.support.WorkspaceCodec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 이전 버전에서 작업공간 JSON(workspaces.content)에만 저장했던 타임라인 사건을
 * 전용 테이블(timeline_events)로 멱등하게 이관한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyTimelineEventMigrator implements ApplicationRunner {
    private final WorkspaceRepository workspaceRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final WorkRepository workRepository;
    private final WorkspaceCodec workspaceCodec;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateLegacyEvents();
    }

    public int migrateLegacyEvents() {
        List<Long> workIds =
                workspaceRepository.findAll().stream()
                        .filter(ws -> ws.getContent() != null && ws.getContent().contains("\"events\""))
                        .map(Workspace::getWorkId)
                        .toList();
        int migratedWorks = 0;
        for (Long workId : workIds) {
            try {
                Boolean migrated =
                        transactionTemplate.execute(status -> migrateWorkEvents(workId));
                if (Boolean.TRUE.equals(migrated)) {
                    migratedWorks++;
                }
            } catch (Exception e) {
                log.warn("작업공간 JSON 사건 이관 건너뜀 (workId={}): {}", workId, e.getMessage());
            }
        }
        return migratedWorks;
    }

    /**
     * 전용 테이블에 아직 사건이 없는 작품이거나, JSON에서 사건 목록을 비우지 않은 작품에 대해
     * 기존 TimelineEvent를 덮어쓰지 않고 누락된 사건만 이관한 뒤 JSON의 events를 비운다.
     */
    public boolean migrateWorkEvents(Long workId) {
        Workspace workspace = workspaceRepository.findById(workId).orElse(null);
        if (workspace == null || workspace.getContent() == null || workspace.getContent().isBlank()) {
            return false;
        }
        WorkspaceData decoded = workspaceCodec.decode(workspace.getContent());
        List<EventData> legacyEvents = decoded.events();
        if (legacyEvents == null || legacyEvents.isEmpty()) {
            return false;
        }
        Work work = workRepository.findById(workId).orElse(null);
        if (work == null) {
            return false;
        }
        List<TimelineEvent> existing =
                timelineEventRepository.findByWork_IdOrderBySequenceAscIdAsc(workId);
        // 이미 timeline_events 테이블에서 사건이 관리되고 있는 작품은 새로 수정·삭제한 상태를 덮어쓰지 않는다.
        if (existing.isEmpty()) {
            Set<String> seenKeys = new HashSet<>();
            int nextSequence = 0;
            for (EventData item : legacyEvents) {
                if (item == null || item.id() == null || item.id().isBlank()) {
                    continue;
                }
                if (!seenKeys.add(item.id())) {
                    continue;
                }
                int sequence = item.sequence() != null ? item.sequence() : nextSequence;
                nextSequence = Math.max(nextSequence, sequence + 1);
                String time = item.time() == null ? "" : item.time();
                String timeStatus =
                        (item.timeStatus() != null && !item.timeStatus().isBlank())
                                ? item.timeStatus()
                                : (!time.isBlank() ? "CONFIRMED" : "UNRESOLVED");
                timelineEventRepository.save(
                        new TimelineEvent(
                                work,
                                item.id(),
                                item.title() == null ? "" : item.title(),
                                item.description() == null ? "" : item.description(),
                                time,
                                item.characters() == null ? "" : item.characters(),
                                item.chapterId() == null ? "" : item.chapterId(),
                                item.quote() == null ? "" : item.quote(),
                                sequence,
                                timeStatus,
                                item.sourceRevision(),
                                item.startOffset(),
                                item.endOffset(),
                                item.extractionId()));
            }
            timelineEventRepository.flush();
        }
        String cleanedContent =
                workspaceCodec.encode(
                        new WorkspaceData(decoded.chapters(), decoded.analyses(), List.of()));
        workspace.clearLegacyEvents(cleanedContent);
        workspaceRepository.saveAndFlush(workspace);
        return true;
    }
}
