package com.magister.backend.domain.scene;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;
import com.magister.backend.domain.aiquota.AiQuotaService;
import com.magister.backend.domain.analysis.service.support.AnalysisPreviewToken;
import com.magister.backend.domain.scene.SceneBoardData.Board;
import com.magister.backend.domain.scene.SceneImageData.CreateRequest;
import com.magister.backend.domain.scene.SceneImageData.Input;
import com.magister.backend.domain.scene.SceneImageData.Job;
import com.magister.backend.domain.scene.SceneImageData.Preview;
import com.magister.backend.domain.scene.SceneImageRepository.StoredImage;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class SceneImageService {
    private final SceneBoardService sceneBoardService;
    private final WorkAccessService workAccessService;
    private final SceneImageRepository sceneImageRepository;
    private final SceneBoardConverter sceneBoardConverter;
    private final AiQuotaService aiQuotaService;
    private final AnalysisPreviewToken analysisPreviewToken;
    private final ObjectMapper objectMapper;
    private final String model;

    SceneImageService(
            SceneBoardService sceneBoardService,
            WorkAccessService workAccessService,
            SceneImageRepository sceneImageRepository,
            SceneBoardConverter sceneBoardConverter,
            AiQuotaService aiQuotaService,
            AnalysisPreviewToken analysisPreviewToken,
            ObjectMapper objectMapper,
            @Value("${scene-board.image-model:}") String model) {
        this.sceneBoardService = sceneBoardService;
        this.workAccessService = workAccessService;
        this.sceneImageRepository = sceneImageRepository;
        this.sceneBoardConverter = sceneBoardConverter;
        this.aiQuotaService = aiQuotaService;
        this.analysisPreviewToken = analysisPreviewToken;
        this.objectMapper = objectMapper;
        this.model = model.strip();
    }

    /** 외부 전송 전에 작가가 확인한 설명과 해당 장면의 고정 버전을 보여준다. */
    @Transactional(readOnly = true)
    public Preview preview(long userId, long workId, String boardId, Input input) {
        Board board = sceneBoardService.get(userId, workId, boardId);
        ensureReady(board);

        String token =
                analysisPreviewToken.issue(
                        payload(userId, workId, board, input), Instant.now().plusSeconds(600));

        return sceneBoardConverter.toImagePreview(input, token, revision(board));
    }

    /** 같은 키의 재전송을 구분한 후 이미지 작업과 한도 차감을 한 번에 저장한다. */
    @Transactional
    public Job create(long userId, long workId, String boardId, CreateRequest request) {
        workAccessService.lockOwned(userId, workId);
        Board board = sceneBoardService.get(userId, workId, boardId);
        if (!request.consent()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        String input = objectMapper.writeValueAsString(request.input());
        Optional<StoredImage> previous =
                sceneImageRepository.findRequest(boardId, request.requestId());
        if (previous.isPresent()) {
            StoredImage image = previous.get();
            if (!image.input().equals(input)) {
                throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
            }
            return sceneBoardConverter.toImageJob(image, board);
        }

        ensureReady(board);
        analysisPreviewToken.verify(
                request.token(), payload(userId, workId, board, request.input()));

        aiQuotaService.charge(userId, Feature.SCENE_IMAGE);
        StoredImage image = new StoredImage(UUID.randomUUID().toString(), input, "QUEUED");
        sceneImageRepository.insert(boardId, request.requestId(), image);

        return sceneBoardConverter.toImageJob(image, board);
    }

    /** 작품 소유자가 생성한 시안의 메타데이터를 페이지 단위로 조회한다. */
    @Transactional(readOnly = true)
    public List<Job> list(long userId, long workId, String boardId, int page) {
        Board board = sceneBoardService.get(userId, workId, boardId);

        return sceneImageRepository.list(boardId, page).stream()
                .map(image -> sceneBoardConverter.toImageJob(image, board))
                .toList();
    }

    /** 작품과 보드에 소속된 개별 작업만 조회한다. */
    @Transactional(readOnly = true)
    public Job get(long userId, long workId, String boardId, String id) {
        Board board = sceneBoardService.get(userId, workId, boardId);

        return sceneImageRepository
                .find(boardId, id)
                .map(image -> sceneBoardConverter.toImageJob(image, board))
                .orElseThrow(() -> new CustomException(SceneErrorCode.SCENE_IMAGE_NOT_FOUND));
    }

    /** 비공개 결과 바이트는 인증된 작품 소유자에게만 전달한다. */
    @Transactional(readOnly = true)
    public byte[] content(long userId, long workId, String boardId, String id) {
        sceneBoardService.get(userId, workId, boardId);

        return sceneImageRepository
                .content(boardId, id)
                .orElseThrow(() -> new CustomException(SceneErrorCode.SCENE_IMAGE_NOT_FOUND));
    }

    private void ensureReady(Board board) {
        if (model.isEmpty()) {
            throw new CustomException(SceneErrorCode.SCENE_IMAGE_UNAVAILABLE);
        }
        if (!"SUCCEEDED".equals(board.status())) {
            throw new CustomException(SceneErrorCode.SCENE_BOARD_NOT_READY);
        }
    }

    private long revision(Board board) {
        return board.snapshot().selection().revision();
    }

    private String payload(long userId, long workId, Board board, Input input) {
        return "scene-image|"
                + userId
                + "|"
                + workId
                + "|"
                + board.id()
                + "|"
                + revision(board)
                + "|"
                + model
                + "|"
                + objectMapper.writeValueAsString(input);
    }
}
