package com.magister.backend.domain.gemini;

import com.magister.backend.domain.gemini.controller.GeminiController;
import com.magister.backend.domain.gemini.controller.GeminiExceptionHandler;
import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.global.gemini.GeminiApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeminiControllerTests {

    private final GeminiService service = mock(GeminiService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new GeminiController(service))
            .setControllerAdvice(new GeminiExceptionHandler()).build();

    @Test
    void returnsText() throws Exception {
        when(service.generate(new GeminiRequest("hello"))).thenReturn(new GeminiResponse("answer"));
        mvc.perform(post("/api/gemini").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("answer"));
    }

    @Test
    void rejectsMissingBlankAndOversizedPrompt() throws Exception {
        for (String body : new String[]{"{}", "{\"prompt\":\" \"}",
                "{\"prompt\":\"" + "a".repeat(2001) + "\"}"}) {
            mvc.perform(post("/api/gemini").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/gemini").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"TIMEOUT,504", "RATE_LIMITED,503", "UNAVAILABLE,503",
            "AUTHENTICATION_FAILED,502", "REQUEST_REJECTED,502", "INVALID_RESPONSE,502"})
    void mapsProviderFailureToProblemDetail(GeminiApiException.ErrorCode code, int expectedStatus)
            throws Exception {
        when(service.generate(any())).thenThrow(new GeminiApiException(code));
        mvc.perform(post("/api/gemini").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
    }
}
