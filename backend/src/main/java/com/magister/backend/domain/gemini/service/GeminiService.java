package com.magister.backend.domain.gemini.service;

import com.magister.backend.domain.gemini.dto.*;
import com.magister.backend.domain.gemini.converter.GeminiConverter;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

@Service
public class GeminiService {
    private static final long INTERVAL = Duration.ofSeconds(5).toNanos();
    private static final long RETENTION = Duration.ofMinutes(10).toNanos();
    private final GeminiClient client;
    private final LongSupplier clock;
    private final Map<Long, Window> windows = new HashMap<>();
    private int active;

    private static class Window {
        boolean busy;
        long started;

        Window(long started) {
            this.started = started;
        }
    }

    @Autowired
    public GeminiService(GeminiClient client) {
        this(client, System::nanoTime);
    }

    public GeminiService(GeminiClient client, LongSupplier clock) {
        this.client = client;
        this.clock = clock;
    }

    public GeminiResponse generate(GeminiRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        long owner = auth != null && auth.getPrincipal() instanceof LoginUser user ? user.id() : 0L;
        return GeminiConverter.toResponse(generateForOwner(owner, request.prompt(), 1024));
    }

    public String analyze(Long owner, String prompt) {
        return generateForOwner(owner, prompt, 4096);
    }

    private String generateForOwner(Long owner, String prompt, int outputLimit) {
        reserve(owner);
        try {
            return client.generateText(prompt, outputLimit);
        } finally {
            release(owner);
        }
    }

    private synchronized void reserve(Long owner) {
        long now = clock.getAsLong();
        windows.entrySet()
                .removeIf(
                        entry ->
                                !entry.getValue().busy
                                        && now - entry.getValue().started > RETENTION);
        Window window = windows.get(owner);
        // 사용자별 시작 간격과 공급자 전체 동시 호출 상한을 분리한다.
        if (active >= 2
                || (window != null && (window.busy || now - window.started < INTERVAL))
                || (window == null && windows.size() >= 10000))
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        if (window == null) {
            window = new Window(now);
            windows.put(owner, window);
        }
        window.busy = true;
        window.started = now;
        active++;
    }

    private synchronized void release(Long owner) {
        windows.get(owner).busy = false;
        active--;
    }
}
