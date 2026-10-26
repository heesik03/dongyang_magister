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
        when(client.generateText("first", 4096)).thenReturn("one");
        when(client.generateText("second", 4096)).thenReturn("two");
        assertThat(service.analyze(1L, "first")).isEqualTo("one");
        assertThat(service.analyze(2L, "second")).isEqualTo("two");
        assertThatThrownBy(() -> service.generate(1L, new GeminiRequest("first")))
                .isInstanceOf(CustomException.class);
        now.addAndGet(5000000000L);
        when(client.generateText("failure", 4096)).thenThrow(new IllegalStateException("test"));
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
        when(client.generateText("queued", 4096)).thenReturn("result");
        CountDownLatch entered = new CountDownLatch(1);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<String> result =
                    caller.submit(
                            () -> {
                                entered.countDown();
                                return service.analyze(3L, "queued");
                            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            verify(client, never()).generateText("queued", 4096);
            first.close();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("result");
        } finally {
            first.close();
            second.close();
        }
    }
}
