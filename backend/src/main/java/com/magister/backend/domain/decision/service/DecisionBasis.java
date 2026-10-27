package com.magister.backend.domain.decision.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 후보 페이지 전체의 현재 원문을 한 번씩 읽고 판단의 근거 변경을 계산한다. */
@Component
@RequiredArgsConstructor
public class DecisionBasis {
    private final ChapterRepository chapterRepository;
    private final WorkSettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    public record State(String hash, boolean originalChanged, boolean quotesMatch) {}

    private record SourceState(
            String content, long revision, String title, String metadata, boolean allowed) {}

    /** 권한 확인한 작품의 후보·근거만 비교한다. 인용 위치가 사라진 판단은 재분석이 필요하다. */
    public Map<String, State> inspect(
            Long workId, List<ReviewCandidate> candidates, List<ReviewEvidence> evidences) {
        Map<Long, AnalysisSource> sources = new LinkedHashMap<>();
        for (ReviewCandidate candidate : candidates) {
            sources.put(candidate.getTargetSource().getId(), candidate.getTargetSource());
        }
        for (ReviewEvidence evidence : evidences) {
            sources.put(evidence.getSource().getId(), evidence.getSource());
        }
        List<String> chapterKeys =
                sources.values().stream()
                        .filter(source -> !source.getType().equals("SETTING"))
                        .map(AnalysisSource::getSourceKey)
                        .distinct()
                        .toList();
        Map<String, Chapter> chapters =
                chapterKeys.isEmpty()
                        ? Map.of()
                        : chapterRepository
                                .findByWork_IdAndChapterKeyIn(workId, chapterKeys)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                Chapter::getChapterKey, Function.identity()));
        List<Long> settingIds =
                sources.values().stream()
                        .filter(source -> source.getType().equals("SETTING"))
                        .map(source -> Long.valueOf(source.getSourceKey()))
                        .distinct()
                        .toList();
        Map<Long, WorkSetting> settings =
                settingIds.isEmpty()
                        ? Map.of()
                        : settingRepository.findAllByWork_IdAndIdIn(workId, settingIds).stream()
                                .collect(Collectors.toMap(WorkSetting::getId, Function.identity()));
        Map<Long, SourceState> current = new HashMap<>();
        for (AnalysisSource source : sources.values()) {
            if (source.getType().equals("SETTING")) {
                WorkSetting setting = settings.get(Long.valueOf(source.getSourceKey()));
                if (setting != null) {
                    current.put(
                            source.getId(),
                            new SourceState(
                                    setting.getContent(),
                                    setting.getVersion(),
                                    setting.getTitle(),
                                    objectMapper.writeValueAsString(
                                            AnalysisConverter.toReference(setting)),
                                    setting.getStatus() == SettingStatus.CONFIRMED));
                }
            } else {
                Chapter chapter = chapters.get(source.getSourceKey());
                if (chapter != null) {
                    current.put(
                            source.getId(),
                            new SourceState(
                                    chapter.getContent(),
                                    chapter.getRevision(),
                                    chapter.getTitle(),
                                    "{}",
                                    true));
                }
            }
        }
        Map<String, List<ReviewEvidence>> byReview =
                evidences.stream()
                        .collect(Collectors.groupingBy(evidence -> evidence.getReview().getId()));
        Map<String, State> result = new HashMap<>();
        for (ReviewCandidate candidate : candidates) {
            List<ReviewEvidence> candidateEvidences =
                    byReview.getOrDefault(candidate.getId(), List.of());
            Map<Long, AnalysisSource> relevant = new HashMap<>();
            relevant.put(candidate.getTargetSource().getId(), candidate.getTargetSource());
            for (ReviewEvidence evidence : candidateEvidences) {
                relevant.put(evidence.getSource().getId(), evidence.getSource());
            }
            List<String> originalValues = new ArrayList<>(), currentValues = new ArrayList<>();
            boolean quotesMatch =
                    matches(
                            current.get(candidate.getTargetSource().getId()),
                            candidate.getQuote(),
                            candidate.getStartOffset(),
                            candidate.getEndOffset());
            for (AnalysisSource source :
                    relevant.values().stream()
                            .sorted(Comparator.comparing(AnalysisSource::getId))
                            .toList()) {
                originalValues.add(
                        encode(
                                source.getId(),
                                new SourceState(
                                        source.getContent(),
                                        source.getRevision(),
                                        source.getTitle(),
                                        source.getMetadata(),
                                        true)));
                currentValues.add(encode(source.getId(), current.get(source.getId())));
            }
            for (ReviewEvidence evidence : candidateEvidences) {
                quotesMatch &=
                        matches(
                                current.get(evidence.getSource().getId()),
                                evidence.getQuote(),
                                evidence.getStartOffset(),
                                evidence.getEndOffset());
            }
            String hash = hash(currentValues);
            result.put(
                    candidate.getId(),
                    new State(hash, !hash.equals(hash(originalValues)), quotesMatch));
        }
        return result;
    }

    private String encode(Long sourceId, SourceState state) {
        return objectMapper.writeValueAsString(
                List.of(sourceId, state == null ? "missing" : state));
    }

    private boolean matches(SourceState source, String quote, int start, int end) {
        return source != null
                && source.allowed()
                && start >= 0
                && end <= source.content().length()
                && source.content().substring(start, end).equals(quote);
    }

    private String hash(List<String> values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            objectMapper
                                                    .writeValueAsString(values)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
