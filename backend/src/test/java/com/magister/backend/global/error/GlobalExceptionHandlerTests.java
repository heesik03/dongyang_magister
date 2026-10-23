package com.magister.backend.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.response.ApiResponse;

import jakarta.validation.Valid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new TestController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @CsvSource({
        "limited,429,TOO_MANY_REQUESTS,WARN",
        "unexpected,500,INTERNAL_SERVER_ERROR,ERROR",
        "input,405,METHOD_NOT_ALLOWED,WARN"
    })
    void logsErrorCodeAndStatusWithoutExceptionDetails(
            String path, int expectedStatus, String expectedCode, String expectedLevel)
            throws Exception {
        mvc.perform(get("/test/" + path))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(header().doesNotExist("X-Trace-Id"));
        assertSafeFailureLog(expectedCode, expectedStatus, expectedLevel);
    }

    @ParameterizedTest
    @CsvSource({"false,INVALID_REQUEST", "true,VALIDATION_ERROR"})
    void logsInputErrorsWithoutRequestBody(boolean validJson, String expectedCode)
            throws Exception {
        String body =
                validJson
                        ? "{\"prompt\":\"\",\"private\":\"sensitive-value\"}"
                        : "{\"prompt\":\"sensitive-value\"";
        mvc.perform(post("/test/input").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(expectedCode));
        assertSafeFailureLog(expectedCode, 400, "WARN");
    }

    private void assertSafeFailureLog(String code, int status, String level) {
        assertThat(appender.list)
                .filteredOn(event -> event.getFormattedMessage().startsWith("API failure:"))
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.toLevel(level));
                            assertThat(event.getFormattedMessage())
                                    .isEqualTo("API failure: code=" + code + ", status=" + status);
                        });
        assertThat(appender.list)
                .allSatisfy(
                        event -> {
                            assertThat(event.getFormattedMessage())
                                    .doesNotContain("sensitive-value", "private detail");
                            assertThat(event.getThrowableProxy()).isNull();
                        });
    }

    @RestController
    static class TestController {
        @GetMapping("/test/limited")
        public void limited() {
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new IllegalStateException(
                    "private detail", new RuntimeException("sensitive-value"));
        }

        @PostMapping("/test/input")
        public ApiResponse<String> input(@Valid @RequestBody GeminiRequest request) {
            return ApiResponse.success("ok");
        }
    }
}
