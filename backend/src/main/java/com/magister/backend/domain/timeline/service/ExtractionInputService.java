package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.timeline.converter.ExtractionConverter;
import com.magister.backend.domain.timeline.dto.ExtractionData.Input;
import com.magister.backend.domain.timeline.dto.ExtractionData.Selection;
import com.magister.backend.domain.timeline.dto.ExtractionData.Source;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtractionInputService {
    private final ChapterRepository chapterRepository;
    private final GeminiProperties geminiProperties;
    private final ExtractionConverter extractionConverter;

    /** 선택 회차 10개·본문 합계 20만 자 안에서 실제 저장 버전만 전송한다. */
    public Input snapshot(Long workId, List<Selection> selected) {
        List<String> keys = selected.stream().map(Selection::id).toList();
        if (keys.isEmpty() || keys.size() > 10 || keys.stream().distinct().count() != keys.size()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        List<Chapter> chapters = chapterRepository.findByWork_IdAndChapterKeyIn(workId, keys);
        if (chapters.size() != keys.size()) {
            throw new CustomException(WorkErrorCode.WORK_NOT_FOUND);
        }
        for (Chapter chapter : chapters) {
            if (selected.stream()
                    .noneMatch(
                            s ->
                                    s.id().equals(chapter.getChapterKey())
                                            && s.revision() == chapter.getRevision())) {
                throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
            }
        }
        Input input =
                extractionConverter.input(
                        chapters.stream()
                                .sorted(Comparator.comparingInt(Chapter::getChapterNo))
                                .toList(),
                        geminiProperties.model());
        List<Source> sources = input.sources();
        int length = sources.stream().mapToInt(source -> source.content().length()).sum();
        if (length == 0
                || length > 200000
                || sources.stream().anyMatch(source -> source.content().isBlank())) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return input;
    }
}
