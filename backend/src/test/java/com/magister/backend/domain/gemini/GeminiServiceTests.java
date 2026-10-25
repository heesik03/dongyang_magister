package com.magister.backend.domain.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.concurrent.atomic.AtomicLong;

class GeminiServiceTests {

    @Test
    void delegatesWithOutputLimitAndRejectsRepeatedCalls() {
        GeminiClient client = mock(GeminiClient.class);
        GeminiService service = new GeminiService(client);
        when(client.generateText("hello", 1024)).thenReturn("answer");
        assertThat(service.generate(new GeminiRequest("hello")).text()).isEqualTo("answer");
        assertThatThrownBy(() -> service.generate(new GeminiRequest("hello")))
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
        var now = new AtomicLong(1000000000L);
        GeminiService service = new GeminiService(client, now::get);
        when(client.generateText("first", 4096)).thenReturn("one");
        when(client.generateText("second", 4096)).thenReturn("two");
        assertThat(service.analyze(1L, "first")).isEqualTo("one");
        assertThat(service.analyze(2L, "second")).isEqualTo("two");
        assertThatThrownBy(() -> service.analyze(1L, "first")).isInstanceOf(CustomException.class);
        now.addAndGet(5000000000L);
        when(client.generateText("failure", 4096)).thenThrow(new IllegalStateException("test"));
        assertThatThrownBy(() -> service.analyze(1L, "failure"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.analyze(2L, "second")).isEqualTo("two");
    }
}
