package com.magister.backend.domain.gemini;

import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.global.gemini.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.magister.backend.global.error.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiServiceTests {

    @Test
    void delegatesWithOutputLimitAndRejectsRepeatedCalls() {
        GeminiClient client = mock(GeminiClient.class);
        GeminiService service = new GeminiService(client);
        when(client.generateText("hello", 1024)).thenReturn("answer");
        assertThat(service.generate(new GeminiRequest("hello")).text()).isEqualTo("answer");
        assertThatThrownBy(() -> service.generate(new GeminiRequest("hello")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getHttpStatus())
                                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(client, times(1)).generateText("hello", 1024);
    }
}
