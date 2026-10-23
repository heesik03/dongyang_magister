package com.magister.backend.domain.gemini;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.gemini.controller.GeminiController;
import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.global.error.GlobalExceptionHandler;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.GeminiApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class GeminiControllerTests {

    private final GeminiService service = mock(GeminiService.class);
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new GeminiController(service))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @Test
    void returnsText() throws Exception {
        when(service.generate(new GeminiRequest("hello"))).thenReturn(new GeminiResponse("answer"));
        mvc.perform(
                        post("/api/gemini")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.text").value("answer"));
    }

    @Test
    void rejectsMissingBlankAndOversizedPrompt() throws Exception {
        for (String body :
                new String[] {
                    "{}", "{\"prompt\":\" \"}", "{\"prompt\":\"" + "a".repeat(2001) + "\"}"
                }) {
            mvc.perform(post("/api/gemini").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("prompt"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/gemini").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void handlesExceptionsFromOtherControllersWithoutExposingDetails() throws Exception {
        MockMvc otherMvc =
                MockMvcBuilders.standaloneSetup(new OtherController())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        otherMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 처리 중 오류가 발생했습니다."));
        otherMvc.perform(get("/test/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void preservesFrameworkErrorStatus() throws Exception {
        mvc.perform(get("/api/gemini"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @RestController
    static class OtherController {
        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new IllegalStateException("private detail");
        }

        @GetMapping("/test/limited")
        public void limited() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "TIMEOUT,504",
        "RATE_LIMITED,503",
        "UNAVAILABLE,503",
        "AUTHENTICATION_FAILED,502",
        "REQUEST_REJECTED,502",
        "INVALID_RESPONSE,502"
    })
    void mapsProviderFailureToCommonResponse(GeminiErrorCode code, int expectedStatus)
            throws Exception {
        when(service.generate(any())).thenThrow(new GeminiApiException(code));
        mvc.perform(
                        post("/api/gemini")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(code.getMessage()))
                .andExpect(jsonPath("$.code").value(code.name()));
    }
}
