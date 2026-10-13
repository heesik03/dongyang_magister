package com.magister.backend.domain.gemini.service;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.global.gemini.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GeminiService {

    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final long CALL_INTERVAL_NANOS = Duration.ofSeconds(5).toNanos();

    private final GeminiClient geminiClient;
    private final Semaphore callPermit = new Semaphore(1);
    private Long lastCallNanos;

    public GeminiResponse generate(GeminiRequest request) {
        if (!callPermit.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            long now = System.nanoTime();
            if (lastCallNanos != null && now - lastCallNanos < CALL_INTERVAL_NANOS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            }
            lastCallNanos = now;
            return new GeminiResponse(geminiClient.generateText(request.prompt(), MAX_OUTPUT_TOKENS));
        } finally {
            callPermit.release();
        }
    }
}
