package com.magister.backend.domain.scene;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;
import com.magister.backend.domain.aiquota.AiQuotaService;
import com.magister.backend.domain.analysis.service.support.AnalysisPreviewToken;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.scene.SceneBoardData.Board;
import com.magister.backend.domain.scene.SceneBoardData.CreateRequest;
import com.magister.backend.domain.scene.SceneBoardData.Preview;
import com.magister.backend.domain.scene.SceneBoardData.Selection;
import com.magister.backend.domain.scene.SceneBoardData.Snapshot;
import com.magister.backend.domain.scene.SceneBoardRepository.StoredBoard;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SceneBoardService {
    private final WorkAccessService workAccessService;
    private final ChapterRepository chapterRepository;
    private final WorkSettingRepository workSettingRepository;
    private final SceneBoardRepository sceneBoardRepository;
    private final AiQuotaService aiQuotaService;
    private final AnalysisPreviewToken analysisPreviewToken;
    private final ObjectMapper objectMapper;
    private final SceneBoardConverter sceneBoardConverter;

    /** 사용자에게 전송할 스냅샷을 확인시킨다. 공급자 호출과 차감은 접수 시점까지 미룬다. */
    @Transactional(readOnly = true)
    public Preview preview(long userId, long workId, Selection selection) {
        workAccessService.requireOwned(userId, workId);

        Snapshot snapshot = snapshot(workId, selection);
        String token =
                analysisPreviewToken.issue(
                        tokenPayload(userId, workId, snapshot), Instant.now().plusSeconds(600));

        return sceneBoardConverter.toPreview(snapshot, token);
    }

    /** 작품 잠금으로 재전송을 직렬화한 뒤 신규 작업 저장과 사용량 차감을 함께 커밋한다. */
    @Transactional
    public Board create(long userId, long workId, CreateRequest request) {
        workAccessService.lockOwned(userId, workId);
        if (!request.consent()) {
            throw invalid();
        }

        String selectionJson = objectMapper.writeValueAsString(request.selection());
        Optional<StoredBoard> existing =
                sceneBoardRepository.findRequest(workId, request.requestId());
        if (existing.isPresent()) {
            StoredBoard board = existing.get();
            if (!board.selection().equals(selectionJson)) {
                throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
            }
            return sceneBoardConverter.toBoard(board);
        }

        Snapshot snapshot = snapshot(workId, request.selection());
        analysisPreviewToken.verify(request.token(), tokenPayload(userId, workId, snapshot));

        aiQuotaService.charge(userId, Feature.SCENE_ANALYSIS);
        StoredBoard board =
                new StoredBoard(
                        UUID.randomUUID().toString(),
                        selectionJson,
                        objectMapper.writeValueAsString(snapshot),
                        "QUEUED",
                        null);
        sceneBoardRepository.insert(
                workId, request.selection().chapterId(), request.requestId(), board);

        return sceneBoardConverter.toBoard(board);
    }

    /** 작품 소유권을 검증한 뒤 생성 당시 원고와 결과를 반환한다. */
    @Transactional(readOnly = true)
    public Board get(long userId, long workId, String id) {
        workAccessService.requireOwned(userId, workId);

        return sceneBoardRepository
                .find(workId, id)
                .map(sceneBoardConverter::toBoard)
                .orElseThrow(() -> new CustomException(SceneErrorCode.SCENE_BOARD_NOT_FOUND));
    }

    /** 원고 본문을 제외한 회차별 보드 식별자를 최대 10개 반환한다. */
    @Transactional(readOnly = true)
    public List<String> list(long userId, long workId, String chapterId, int page) {
        workAccessService.requireOwned(userId, workId);

        return sceneBoardRepository.listIds(workId, chapterId, page);
    }

    private Snapshot snapshot(long workId, Selection selection) {
        Chapter chapter =
                chapterRepository
                        .findByWork_IdAndChapterKey(workId, selection.chapterId())
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        if (chapter.getRevision() != selection.revision()) {
            throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
        }
        if (selection.end() <= selection.start()
                || selection.end() > chapter.getContent().length()
                || selection.end() - selection.start() > 12_000
                || splitsSurrogate(chapter.getContent(), selection.start())
                || splitsSurrogate(chapter.getContent(), selection.end())) {
            throw invalid();
        }

        String text = chapter.getContent().substring(selection.start(), selection.end());
        if (text.isBlank()) {
            throw invalid();
        }

        List<WorkSetting> settings =
                selection.settingIds().isEmpty()
                        ? List.of()
                        : workSettingRepository.findAllByWork_IdAndIdIn(
                                workId, selection.settingIds());
        if (settings.size() != selection.settingIds().size()
                || settings.stream()
                        .anyMatch(setting -> setting.getStatus() != SettingStatus.CONFIRMED)
                || settings.stream().mapToInt(setting -> setting.getContent().length()).sum()
                        > 20_000) {
            throw invalid();
        }

        return sceneBoardConverter.toSnapshot(selection, chapter.getTitle(), text, settings);
    }

    private boolean splitsSurrogate(String text, int index) {
        return index > 0
                && index < text.length()
                && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1));
    }

    private String tokenPayload(long userId, long workId, Snapshot snapshot) {
        return "scene-board|"
                + userId
                + "|"
                + workId
                + "|"
                + objectMapper.writeValueAsString(snapshot);
    }

    private CustomException invalid() {
        return new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
