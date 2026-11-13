package com.magister.backend.domain.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.domain.gemini.service.limit.GeminiRateLimiter;
import com.magister.backend.domain.gemini.service.limit.GeminiRateLimiter.Reservation;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class GeminiServiceTests {

    @Test
    void delegatesWithOutputLimitAndRejectsRepeatedCalls() {
        GeminiClient client = mock(GeminiClient.class);
        GeminiService service = new GeminiService(client, new GeminiRateLimiter());
        when(client.generateText("hello", 1024)).thenReturn("answer");
        assertThat(service.generate(1L, new GeminiRequest("hello")).text()).isEqualTo("answer");
        assertThatThrownBy(() -> service.generate(1L, new GeminiRequest("hello")))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode().getHttpStatus())
                                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(client, times(1)).generateText("hello", 1024);
    }

    @Test
    void usersHaveIndependentStartIntervalsAndFailedCallsReleaseCapacity() {
        GeminiClient client = mock(GeminiClient.class);
        AtomicLong now = new AtomicLong(1000000000L);
        GeminiService service = new GeminiService(client, new GeminiRateLimiter(now::get));
        when(client.generateAnalysis("first")).thenReturn("one");
        when(client.generateAnalysis("second")).thenReturn("two");
        assertThat(service.analyze(1L, "first")).isEqualTo("one");
        assertThat(service.analyze(2L, "second")).isEqualTo("two");
        assertThatThrownBy(() -> service.generate(1L, new GeminiRequest("first")))
                .isInstanceOf(CustomException.class);
        now.addAndGet(5000000000L);
        when(client.generateAnalysis("failure")).thenThrow(new IllegalStateException("test"));
        assertThatThrownBy(() -> service.analyze(1L, "failure"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.analyze(2L, "second")).isEqualTo("two");
    }

    @Test
    void acceptedAnalysisWaitsForProviderCapacityBeforeSending() throws Exception {
        GeminiClient client = mock(GeminiClient.class);
        GeminiRateLimiter limiter = new GeminiRateLimiter();
        GeminiService service = new GeminiService(client, limiter);
        Reservation first = limiter.reserve(1L);
        Reservation second = limiter.reserve(2L);
        when(client.generateAnalysis("queued")).thenReturn("result");
        CountDownLatch entered = new CountDownLatch(1);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<String> result =
                    caller.submit(
                            () -> {
                                entered.countDown();
                                return service.analyze(3L, "queued");
                            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            verify(client, never()).generateAnalysis("queued");
            first.close();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("result");
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void formatsPromptWhenWritingActionIsProvided() {
        GeminiClient client = mock(GeminiClient.class);
        GeminiService service = new GeminiService(client, new GeminiRateLimiter());
        when(client.generateText(
                        "다음 소설 원고의 문체와 흐름을 유지하며 자연스럽게 이어질 다음 문단을 한국어로 작성해 주세요.\n\n린은 검을 뽑았다.",
                        1024))
                .thenReturn("차가운 달빛이 검신 위로 흘러내렸다.");
        assertThat(service.generate(1L, new GeminiRequest("린은 검을 뽑았다.", "CONTINUE")).text())
                .isEqualTo("차가운 달빛이 검신 위로 흘러내렸다.");
    }
}
