package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.RevisionSuggestionConverter;
import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class RevisionSuggestionService {
    private static final String PROMPT_HEADER = loadPrompt("prompts/revision-suggestion.txt") + "\n";

    private final RevisionSuggestionContextReader contextReader;
    private final RevisionSuggestionConverter converter;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    /** 확인한 미리보기와 일치할 때만 사용자 한도를 적용해 대체 문장을 생성한다. */
    public RevisionSuggestion.Result generate(
            Long ownerId, Long workId, String reviewId, String contextHash) {
        RevisionSuggestion.Context context = contextReader.read(ownerId, workId, reviewId);
        if (!context.contextHash().equals(contextHash)) {
            throw new CustomException(AnalysisErrorCode.PREVIEW_CHANGED);
        }

        String prompt = PROMPT_HEADER + objectMapper.writeValueAsString(context.material());
        GeminiResponse response = geminiService.generate(ownerId, new GeminiRequest(prompt));

        return converter.toResult(context.target(), response.text());
    }

    private static String loadPrompt(String path) {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            String text =
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("\r\n", "\n")
                            .strip();
            if (text.isEmpty()) {
                throw new IllegalStateException("Prompt resource is blank: " + path);
            }
            return text;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to load prompt resource: " + path, error);
        }
    }
}
