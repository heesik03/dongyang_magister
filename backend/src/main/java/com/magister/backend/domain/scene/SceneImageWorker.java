package com.magister.backend.domain.scene;

import com.magister.backend.domain.scene.SceneImageData.Input;
import com.magister.backend.domain.scene.SceneImageRepository.StoredImage;
import com.magister.backend.global.gemini.GeminiClient;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;

@Component
@Slf4j
class SceneImageWorker {
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final SceneImageRepository repository;
    private final GeminiClient geminiClient;
    private final ObjectMapper mapper;
    private final String model;

    SceneImageWorker(SceneImageRepository repository, GeminiClient geminiClient, ObjectMapper mapper,
            @Value("${scene-board.image-model:}") String model) {
        this.repository = repository;
        this.geminiClient = geminiClient;
        this.mapper = mapper;
        this.model = model.strip();
    }

    /** 텍스트 결과와 독립적으로 작업을 선점하고, 응답 크기와 PNG 형식을 제한한다. */
    @Scheduled(fixedDelayString = "${scene-board.image-poll-delay-ms:3500}", scheduler = "sceneBoardScheduler")
    public void process() {
        if (model.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        repository.expire(now);
        repository.claim(now.plusSeconds(300)).ifPresent(this::generate);
    }

    private void generate(StoredImage image) {
        try {
            Input input = mapper.readValue(image.input(), Input.class);
            String view = switch (input.kind()) {
                case LOCATION -> "장소 전경";
                case PROP -> "소품 확대";
            };
            String prompt = """
                    소설 장면의 %s 콘셉트 이미지를 생성하세요. 인물과 문자 없이 장소와 소품만 보여주세요.
                    작가가 확인한 설정: %s
                    작가가 추가한 이미지 연출: %s
                    """.formatted(view, input.confirmed(), input.direction());
            byte[] content = geminiClient.generateSceneImage(model, prompt);
            if (content.length > 4 * 1024 * 1024 || content.length <= PNG_SIGNATURE.length
                    || !Arrays.equals(content, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
                throw new IllegalArgumentException("Unexpected image response");
            }
            repository.finish(image.id(), "SUCCEEDED", content);
        } catch (RuntimeException exception) {
            log.warn("Scene image failed: id={}, type={}", image.id(), exception.getClass().getSimpleName());
            repository.finish(image.id(), "FAILED", null);
        }
    }
}
