package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisSnapshotResponse;
import com.magister.backend.domain.analysis.dto.AnalysisSourceResponse;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.service.WorkspaceSnapshotService;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisSnapshotService {
    private final AnalysisSourceRepository sourceRepository;
    private final AnalysisJobRepository jobRepository;
    private final ChapterRepository chapterRepository;
    private final WorkSettingRepository settingRepository;
    private final WorkspaceSnapshotService workspaceSnapshotService;
    private final AnalysisConverter analysisConverter;
    private final ObjectMapper objectMapper;

    /** 본인 작업에 포함된 불변 원문 하나만 반환한다. 다른 작업의 출처 ID도 거절한다. */
    @Transactional(readOnly = true)
    public AnalysisSourceResponse getSource(
            Long ownerId, Long workId, String jobId, Long sourceId) {
        jobRepository
                .findOwned(jobId, workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        return analysisConverter.toSource(
                sourceRepository
                        .findByIdAndJob_Id(sourceId, jobId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND)));
    }

    /** 부모 작품 잠금 아래에서 작업·직렬화 입력과 같은 자료를 개별 근거로 한 번만 기록한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AnalysisJob job, AnalysisInput input) {
        Long workId = job.getWork().getId();
        Map<String, Chapter> chapters =
                chapterRepository.findByWork_IdOrderByChapterNoAsc(workId).stream()
                        .collect(Collectors.toMap(Chapter::getChapterKey, Function.identity()));
        Map<String, WorkSetting> settings =
                settingRepository
                        .findAllByWork_IdAndIdIn(
                                workId,
                                input.settings().stream()
                                        .map(item -> Long.valueOf(item.id()))
                                        .toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        item -> item.getId().toString(), Function.identity()));
        List<AnalysisSource> sources = new ArrayList<>();
        sources.add(
                fromChapter(job, chapters.get(input.chapter().id()), "TARGET", input.chapter()));
        for (ChapterData chapter : input.previous()) {
            sources.add(fromChapter(job, chapters.get(chapter.id()), "PREVIOUS", chapter));
        }
        for (SettingReference setting : input.settings()) {
            sources.add(
                    new AnalysisSource(
                            job,
                            null,
                            settings.get(setting.id()),
                            "SETTING",
                            setting.id(),
                            setting.version(),
                            setting.title(),
                            setting.content(),
                            objectMapper.writeValueAsString(setting),
                            hash(setting.content())));
        }
        sourceRepository.saveAll(sources);
    }

    /** 소유한 작업의 입력과 개별 근거를 반환한다. 구형 작업은 기존 input을 원문으로 보존한다. */
    @Transactional(readOnly = true)
    public AnalysisSnapshotResponse get(Long ownerId, Long workId, String jobId) {
        AnalysisJob job =
                jobRepository
                        .findOwned(jobId, workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        AnalysisInput input;
        try {
            input =
                    Objects.requireNonNull(
                            objectMapper.readValue(job.getInput(), AnalysisInput.class));
            for (SettingReference setting : input.settings()) {
                Long.valueOf(setting.id());
            }
        } catch (RuntimeException invalid) {
            throw new CustomException(AnalysisErrorCode.SNAPSHOT_UNAVAILABLE);
        }
        WorkspaceData current = workspaceSnapshotService.getForOwnedWork(job.getWork()).content();
        boolean stale =
                !current.chapters().contains(input.chapter())
                        || !current.chapters().containsAll(input.previous());
        List<SettingReference> settings =
                settingRepository
                        .findAllByWork_IdAndIdIn(
                                workId,
                                input.settings().stream()
                                        .map(item -> Long.valueOf(item.id()))
                                        .toList())
                        .stream()
                        .map(AnalysisConverter::toReference)
                        .toList();
        stale =
                stale
                        || settings.size() != input.settings().size()
                        || !settings.containsAll(input.settings());
        return analysisConverter.toSnapshot(
                job, input, sourceRepository.findByJob_IdOrderByIdAsc(jobId), stale);
    }

    private AnalysisSource fromChapter(
            AnalysisJob job, Chapter entity, String type, ChapterData chapter) {
        return new AnalysisSource(
                job,
                entity,
                null,
                type,
                chapter.id(),
                chapter.revision(),
                chapter.title(),
                chapter.content(),
                "{}",
                hash(chapter.content()));
    }

    private String hash(String content) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
