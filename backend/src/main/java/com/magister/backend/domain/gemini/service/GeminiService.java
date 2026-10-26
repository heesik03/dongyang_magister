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
        try (Reservation reservation = rateLimiter.reserve(ownerId)) {
            return GeminiConverter.toResponse(geminiClient.generateText(request.prompt(), 1024));
        }
    }

    /** 이미 접수된 분석은 슬롯 부족을 실패로 처리하지 않고 전송 전에 대기한다. */
    public String analyze(Long ownerId, String prompt) {
        try (Reservation reservation = rateLimiter.awaitReservation(ownerId)) {
            return geminiClient.generateText(prompt, 4096);
        }
    }
}
