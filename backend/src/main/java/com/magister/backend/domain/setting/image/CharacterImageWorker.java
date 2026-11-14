package com.magister.backend.domain.setting.image;

import com.magister.backend.domain.setting.image.CharacterImageRepository.Pending;
import com.magister.backend.domain.setting.image.CharacterImageRepository.StoredImage;
import com.magister.backend.global.gemini.GeminiClient;
import com.magister.backend.global.gemini.GeminiClient.GeneratedPortrait;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
class CharacterImageWorker {
    private final CharacterImageRepository repository;
    private final GeminiClient geminiClient;

    /** 커밋된 작업을 선점하고 공급자 응답을 트랜잭션 밖에서 기다린다. */
    @Scheduled(fixedDelayString = "${character-image.poll-delay-ms:3000}")
    public void process() {
        Instant now = Instant.now();
        repository.expire(now);
        repository.claim(now.plusSeconds(300)).ifPresent(this::generate);
    }

    private void generate(Pending job) {
        StoredImage image = null;
        try {
            GeneratedPortrait portrait = geminiClient.generatePortrait(job.model(), job.prompt());
            image = new StoredImage(portrait.mimeType(), portrait.bytes());
        } catch (RuntimeException exception) {
            log.warn("Character image generation failed: id={}, type={}", job.id(), exception.getClass().getSimpleName());
        }
        repository.finish(job.id(), image);
    }
}
