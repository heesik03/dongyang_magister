package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.timeline.dto.TimelineData.SaveEventRequest;
import com.magister.backend.domain.timeline.entity.TimelineEvent;
import com.magister.backend.domain.timeline.repository.TimelineEventRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TimelineService {
    private final WorkAccessService workAccessService;
    private final TimelineEventRepository timelineEventRepository;
    private final ChapterRepository chapterRepository;

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
            return updateEntity(existing, request, work);
        }
        long count = timelineEventRepository.countByWork_Id(workId);
        if (count >= 1000) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        int sequence = request.sequence() != null ? request.sequence() : (int) count;
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
    }

    @Transactional
    public List<EventData> replaceAll(Long ownerId, Long workId, List<EventData> events) {
        Work work = workAccessService.lockOwned(ownerId, workId);
        syncForOwnedWork(work, events);
        work.touch();
        return readForOwnedWork(workId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void syncForOwnedWork(Work work, List<EventData> requested) {
        Long workId = work.getId();
        if (requested.size() > 1000) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        Set<String> validChapters = new HashSet<>();
        chapterRepository
                .findByWork_IdOrderByChapterNoAsc(workId)
                .forEach(chapter -> validChapters.add(chapter.getChapterKey()));
        Set<String> requestedKeys = new HashSet<>();
        for (EventData event : requested) {
            if (!requestedKeys.add(event.id())) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
            if (event.chapterId() != null
                    && !event.chapterId().isBlank()
                    && !validChapters.contains(event.chapterId())) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }
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
        Integer sequence = request.sequence() != null ? request.sequence() : entity.getSequence();
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
        return toEventData(entity);
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
