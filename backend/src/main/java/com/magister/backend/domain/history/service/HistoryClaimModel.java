package com.magister.backend.domain.history.service;

import com.magister.backend.domain.aiquota.AiQuotaService;
import com.magister.backend.domain.history.dto.HistoryCheck.Assessments;
import com.magister.backend.domain.history.dto.HistoryCheck.Claim;
import com.magister.backend.domain.history.dto.HistoryCheck.Extraction;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.GeminiApiException;
import com.magister.backend.global.gemini.GeminiClient;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
class HistoryClaimModel {
    private static final String EXTRACT_PROMPT = loadPrompt("prompts/history-claim-extract.txt");
    private static final String ASSESS_PROMPT = loadPrompt("prompts/history-claim-assess.txt");

    private static String loadPrompt(String path) {
        try {
            String text = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                throw new IllegalStateException("Empty prompt: " + path);
            }
            return text + "\n";
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read prompt: " + path, error);
        }
    }

    private final GeminiClient gemini;
    private final AiQuotaService quota;
    private final ObjectMapper mapper;
    private final Validator validator;

    record Input(int index, Claim claim, List<HistoryEvidence> evidence) {}

    Extraction extract(long ownerId, String manuscript) {
        return call(ownerId, EXTRACT_PROMPT + mapper.writeValueAsString(manuscript), Extraction.class);
    }

    Assessments assess(long ownerId, List<Input> inputs) {
        return call(ownerId, ASSESS_PROMPT + mapper.writeValueAsString(inputs), Assessments.class);
    }

    private <T> T call(long ownerId, String prompt, Class<T> type) {
        quota.chargeGeminiText(ownerId);
        String json = gemini.generateHistoryJson(prompt);
        try {
            T output = mapper.readValue(json, type);
            if (output == null || !validator.validate(output).isEmpty()) {
                throw invalidOutput();
            }
            return output;
        } catch (JacksonException exception) {
            throw invalidOutput();
        }
    }

    static GeminiApiException invalidOutput() {
        return new GeminiApiException(GeminiErrorCode.INVALID_RESPONSE);
    }
}
