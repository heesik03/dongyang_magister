package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.domain.timeline.service.ExtractionStateService.Claim;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExtractionWorker {
    private final ExtractionStateService extractionStateService;
    private final ExtractionResultProcessor resultProcessor;
    private final GeminiService geminiService;

    /** 원고 스냅샷을 선점하고 DB 트랜잭션 밖에서 외부 AI를 호출한다. */
    public void execute(String id) {
        try {
            Optional<Claim> claim = extractionStateService.claim(id);
            if (claim.isEmpty()) {
                return;
            }
            String raw =
                    geminiService.extractEvents(
                            claim.get().ownerId(), resultProcessor.prompt(claim.get().input()));
            extractionStateService.succeed(id, resultProcessor.parse(raw, claim.get().input()));
        } catch (Exception error) {
            log.warn(
                    "Event extraction failed: id={}, type={}",
                    id,
                    error.getClass().getSimpleName());
            extractionStateService.fail(id);
        }
    }
}
