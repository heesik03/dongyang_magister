package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.timeline.dto.TimelineData.SaveEventRequest;
import com.magister.backend.domain.timeline.dto.TimelineData.TimelineResponse;
import com.magister.backend.domain.timeline.entity.TimelineEvent;
import com.magister.backend.domain.timeline.repository.TimelineEventRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.domain.workspace.service.support.WorkspaceCodec;
import com.magister.backend.domain.workspace.service.validation.WorkspaceReferenceValidator;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TimelineService {
    private final WorkAccessService workAccessService;
    private final TimelineEventRepository timelineEventRepository;
    private final ChapterRepository chapterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceCodec workspaceCodec;
    private final WorkspaceReferenceValidator referenceValidator;

    @Transactional(readOnly = true)
    public List<EventData> list(Long ownerId, Long workId) {
        workAccessService.requireOwned(ownerId, workId);
        return readForOwnedWork(workId);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<EventData> readForOwnedWork(Long workId) {
        return timelineEventRepository.findByWork_IdOrderBySequenceAscIdAsc(workId).stream()
                .map(this::toEventData)
                .toList();
    }

    @Transactional
    public EventData create(Long ownerId, Long workId, SaveEventRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        validateChapterReference(workId, request.chapterId());
        TimelineEvent existing =
                timelineEventRepository.findByWork_IdAndEventKey(workId, request.id()).orElse(null);
        if (existing != null) {
            EventData current = toEventData(existing);
            EventData repeated = requestEvent(request, current.sequence());
            if (!current.equals(repeated)) {
                throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
            }
            return current;
        }
        long count = timelineEventRepository.countByWork_Id(workId);
        if (count >= 1000) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        int sequence = request.sequence() != null ? request.sequence() : (int) count;
        validateEvidence(requestEvent(request, sequence), null, workId);
        String timeStatus = resolveTimeStatus(request.timeStatus(), request.time());
        TimelineEvent created =
                new TimelineEvent(
                        work,
                        request.id(),
                        request.title(),
                        request.description(),
                        request.time(),
                        request.characters(),
                        request.chapterId(),
                        request.quote(),
                        sequence,
                        timeStatus,
                        request.sourceRevision(),
                        request.startOffset(),
                        request.endOffset(),
                        request.extractionId());
        work.touch();
        timelineEventRepository.saveAndFlush(created);
        advanceWorkspace(work);
        return toEventData(created);
    }

    @Transactional
    public EventData update(
            Long ownerId, Long workId, String eventId, SaveEventRequest request) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        validateChapterReference(workId, request.chapterId());
        TimelineEvent existing =
                timelineEventRepository
                        .findByWork_IdAndEventKey(workId, eventId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        return updateEntity(existing, request, work);
    }

    @Transactional
    public void delete(Long ownerId, Long workId, String eventId) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        TimelineEvent existing =
                timelineEventRepository
                        .findByWork_IdAndEventKey(workId, eventId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        timelineEventRepository.delete(existing);
        work.touch();
        timelineEventRepository.flush();
        advanceWorkspace(work);
    }

    /** 작업공간 버전 검사와 사건 교체를 같은 작품 잠금 안에서 수행한다. */
    @Transactional
    public TimelineResponse replaceAll(
            Long ownerId, Long workId, long version, List<EventData> events) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        return replaceForOwnedWork(work, version, events, Set.of(), null);
    }

    /** 추출 서비스가 잠근 작품에서 서버 후보를 확정하고 동일한 버전 규칙을 적용한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TimelineResponse replaceForOwnedWork(
            Work work,
            long version,
            List<EventData> events,
            Set<String> verifiedExtractions,
            String mutationId) {
        Long workId = work.getId();
        Workspace workspace =
                workspaceRepository.findById(workId).orElseGet(() -> new Workspace(work));
        if (workspace.getRevision() != version) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        syncForOwnedWork(work, events, verifiedExtractions, false);
        work.touch();
        advanceWorkspace(workspace, mutationId);
        return new TimelineResponse(
                workspace.getRevision(), work.getVersion(), readForOwnedWork(workId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void syncForOwnedWork(Work work, List<EventData> requested) {
        syncForOwnedWork(work, requested, Set.of(), true);
    }

    private void syncForOwnedWork(
            Work work,
            List<EventData> requested,
            Set<String> verifiedExtractions,
            boolean allowLegacyQuote) {
        Long workId = work.getId();
        if (requested.size() > 1000) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        Set<String> requestedKeys = new HashSet<>();
        Set<String> chapterKeys = new HashSet<>();
        for (EventData event : requested) {
            if (!requestedKeys.add(event.id())) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
            if (!event.chapterId().isBlank()) {
                chapterKeys.add(event.chapterId());
            }
        }
        Map<String, Chapter> chapters = new HashMap<>();
        if (!chapterKeys.isEmpty()) {
            chapterRepository.findByWork_IdAndChapterKeyIn(workId, chapterKeys.stream().toList())
                    .forEach(chapter -> chapters.put(chapter.getChapterKey(), chapter));
        }
        if (chapters.size() != chapterKeys.size()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        List<TimelineEvent> existingList =
                timelineEventRepository.findByWork_IdOrderBySequenceAscIdAsc(workId);
        Map<String, TimelineEvent> existingByKey = new HashMap<>();
        List<TimelineEvent> toDelete = new ArrayList<>();
        for (TimelineEvent existing : existingList) {
            if (requestedKeys.contains(existing.getEventKey())) {
                existingByKey.put(existing.getEventKey(), existing);
            } else {
                toDelete.add(existing);
            }
        }
        for (EventData event : requested) {
            TimelineEvent previous = existingByKey.get(event.id());
            if (!verifiedExtractions.contains(event.id())) {
                validateEvidence(
                        event,
                        previous == null ? null : toEventData(previous),
                        chapters.get(event.chapterId()),
                        allowLegacyQuote);
            }
        }
        if (!toDelete.isEmpty()) {
            timelineEventRepository.deleteAll(toDelete);
            timelineEventRepository.flush();
        }
        for (EventData item : requested) {
            TimelineEvent current = existingByKey.get(item.id());
            if (current != null) {
                current.update(
                        item.title(),
                        item.description() == null ? "" : item.description(),
                        item.time() == null ? "" : item.time(),
                        item.characters() == null ? "" : item.characters(),
                        item.chapterId() == null ? "" : item.chapterId(),
                        item.quote() == null ? "" : item.quote(),
                        item.sequence(),
                        item.timeStatus(),
                        item.sourceRevision(),
                        item.startOffset(),
                        item.endOffset(),
                        item.extractionId());
            } else {
                timelineEventRepository.save(
                        new TimelineEvent(
                                work,
                                item.id(),
                                item.title(),
                                item.description() == null ? "" : item.description(),
                                item.time() == null ? "" : item.time(),
                                item.characters() == null ? "" : item.characters(),
                                item.chapterId() == null ? "" : item.chapterId(),
                                item.quote() == null ? "" : item.quote(),
                                item.sequence(),
                                item.timeStatus(),
                                item.sourceRevision(),
                                item.startOffset(),
                                item.endOffset(),
                                item.extractionId()));
            }
        }
        timelineEventRepository.flush();
    }

    private EventData updateEntity(TimelineEvent entity, SaveEventRequest request, Work work) {
        if (!entity.getEventKey().equals(request.id())) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        Integer sequence = request.sequence() != null ? request.sequence() : entity.getSequence();
        validateEvidence(requestEvent(request, sequence), toEventData(entity), work.getId());
        String timeStatus = resolveTimeStatus(request.timeStatus(), request.time());
        entity.update(
                request.title(),
                request.description(),
                request.time(),
                request.characters(),
                request.chapterId(),
                request.quote(),
                sequence,
                timeStatus,
                request.sourceRevision(),
                request.startOffset(),
                request.endOffset(),
                request.extractionId());
        work.touch();
        timelineEventRepository.flush();
        advanceWorkspace(work);
        return toEventData(entity);
    }

    private void advanceWorkspace(Work work) {
        advanceWorkspace(
                workspaceRepository.findById(work.getId()).orElseGet(() -> new Workspace(work)),
                null);
    }

    private void advanceWorkspace(Workspace workspace, String mutationId) {
        String content = workspace.getContent();
        if (content == null) {
            content = workspaceCodec.encode(WorkspaceData.empty());
        }
        workspace.replace(content, mutationId);
        workspaceRepository.saveAndFlush(workspace);
    }

    private EventData requestEvent(SaveEventRequest request, Integer sequence) {
        return new EventData(request.id(), request.title(), request.time(), request.characters(),
                request.chapterId(), request.quote(), request.description(), sequence,
                resolveTimeStatus(request.timeStatus(), request.time()), request.sourceRevision(),
                request.startOffset(), request.endOffset(), request.extractionId());
    }

    private void validateEvidence(EventData event, EventData previous, Long workId) {
        Chapter chapter = event.chapterId().isBlank() ? null
                : chapterRepository.findByWork_IdAndChapterKey(workId, event.chapterId()).orElse(null);
        validateEvidence(event, previous, chapter, false);
    }

    private void validateEvidence(
            EventData event, EventData previous, Chapter chapter, boolean allowLegacyQuote) {
        if (previous != null
                && previous.chapterId().equals(event.chapterId())
                && previous.quote().equals(event.quote())
                && Objects.equals(previous.sourceRevision(), event.sourceRevision())
                && Objects.equals(previous.startOffset(), event.startOffset())
                && Objects.equals(previous.endOffset(), event.endOffset())
                && Objects.equals(previous.extractionId(), event.extractionId())) {
            return;
        }
        if (event.extractionId() != null) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        boolean noLocation = event.sourceRevision() == null
                && event.startOffset() == null && event.endOffset() == null;
        if (noLocation && (event.quote().isEmpty()
                || (allowLegacyQuote && chapter != null && !event.quote().isBlank()
                    && chapter.getContent().contains(event.quote())))) {
            return;
        }
        if (chapter == null) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        referenceValidator.validateEventQuote(event, chapter.getContent(), chapter.getRevision());
    }

    private void validateChapterReference(Long workId, String chapterId) {
        if (chapterId != null
                && !chapterId.isBlank()
                && chapterRepository.findByWork_IdAndChapterKey(workId, chapterId).isEmpty()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private String resolveTimeStatus(String timeStatus, String time) {
        if (timeStatus != null && !timeStatus.isBlank()) {
            return timeStatus;
        }
        return time != null && !time.isBlank() ? "CONFIRMED" : "UNRESOLVED";
    }

    private EventData toEventData(TimelineEvent event) {
        return new EventData(
                event.getEventKey(),
                event.getTitle(),
                event.getTime(),
                event.getCharacters(),
                event.getChapterId(),
                event.getQuote(),
                event.getDescription(),
                event.getSequence(),
                event.getTimeStatus(),
                event.getSourceRevision(),
                event.getStartOffset(),
                event.getEndOffset(),
                event.getExtractionId());
    }
}
