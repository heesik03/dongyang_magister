package com.magister.backend.domain.gemini.service;

import com.magister.backend.domain.gemini.converter.GeminiConverter;
import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.domain.gemini.service.limit.GeminiRateLimiter;
import com.magister.backend.domain.gemini.service.limit.GeminiRateLimiter.Reservation;
import com.magister.backend.global.gemini.GeminiClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiService {
    private final GeminiClient geminiClient;
    private final GeminiRateLimiter rateLimiter;

    /** 인증된 ID를 명시적으로 받아 SecurityContext와 비동기 스레드의 결합을 제거한다. */
    public GeminiResponse generate(Long ownerId, GeminiRequest request) {
        String effectivePrompt = formatWritingPrompt(request);
        try (Reservation reservation = rateLimiter.reserve(ownerId)) {
            return GeminiConverter.toResponse(geminiClient.generateText(effectivePrompt, 1024));
        }
    }

    /** 이미 접수된 분석은 슬롯 부족을 실패로 처리하지 않고 전송 전에 대기한다. */
    public String analyze(Long ownerId, String prompt) {
        try (Reservation reservation = rateLimiter.awaitReservation(ownerId)) {
            return geminiClient.generateAnalysis(prompt);
        }
    }

    /** 사건 추출도 같은 사용자별 호출 제한과 공급자 슬롯을 사용한다. */
    public String extractEvents(Long ownerId, String prompt) {
        try (Reservation reservation = rateLimiter.awaitReservation(ownerId)) {
            return geminiClient.generateEvents(prompt);
        }
    }

    private String formatWritingPrompt(GeminiRequest request) {
        if (request.action() == null) {
            return request.prompt();
        }
        return switch (request.action()) {
            case "CONTINUE" ->
                    "다음 소설 원고의 문체와 흐름을 유지하며 자연스럽게 이어질 다음 문단을 한국어로 작성해 주세요.\n\n"
                            + request.prompt();
            case "DESCRIBE" ->
                    "다음 소설 장면의 분위기와 감각적 묘사를 살려 한국어 소설 문장으로 풍부하게 다듬어 주세요.\n\n"
                            + request.prompt();
            case "DIALOGUE" ->
                    "다음 소설 대사와 전후 맥락을 바탕으로 인물의 감정이 살아나는 자연스러운 한국어 대사로 다듬어 주세요.\n\n"
                            + request.prompt();
            default -> request.prompt();
        };
    }
}
