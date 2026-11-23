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
import java.util.ArrayList;
import com.magister.backend.domain.history.dto.HistoryCheck.Assessment;
import com.magister.backend.domain.history.dto.HistoryCheck.Status;

@Component
@RequiredArgsConstructor
class HistoryClaimModel {
    private static final String EXTRACT_PROMPT = loadPrompt("prompts/history-claim-extract.txt");
    private static final String ASSESS_PROMPT = loadPrompt("prompts/history-claim-assess.txt");

    private static String loadPrompt(String path) {
        try {
            String text =
                    new ClassPathResource(path)
                            .getContentAsString(StandardCharsets.UTF_8)
                            .strip();
            if (text.isEmpty()) {
                throw new IllegalStateException("Empty prompt: " + path);
            }
            return text + "\n";
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read prompt: " + path, error);
        }
    }

    private final GeminiClient geminiClient;
    private final AiQuotaService quotaService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    record Input(int index, Claim claim, List<HistoryEvidence> evidence) {}

    Extraction extract(long ownerId, String manuscript) {
        return call(
                ownerId,
                EXTRACT_PROMPT + objectMapper.writeValueAsString(manuscript),
                Extraction.class);
    }

    Assessments assess(long ownerId, List<Input> inputs) {
        List<Assessment> combined = new ArrayList<>();
        List<Input> batch = new ArrayList<>();
        int characters = 0;
        for (Input input : inputs) {
            int size = input.evidence().stream().mapToInt(item -> item.passage().length()).sum();
            if (!batch.isEmpty() && characters + size > DOCUMENT_BATCH_CHARS) {
                combined.addAll(assessBatch(ownerId, batch));
                batch.clear();
                characters = 0;
            }
            if (size <= DOCUMENT_BATCH_CHARS) {
                batch.add(input);
                characters += size;
            } else {
                combined.add(assessLongDocument(ownerId, input));
            }
        }
        if (!batch.isEmpty()) {
            combined.addAll(assessBatch(ownerId, batch));
        }
        return new Assessments(List.copyOf(combined));
    }

    private static final int DOCUMENT_BATCH_CHARS = 24000;

    private List<Assessment> assessBatch(long ownerId, List<Input> inputs) {
        return call(ownerId, ASSESS_PROMPT + objectMapper.writeValueAsString(inputs),
                Assessments.class).assessments();
    }

    private Assessment assessLongDocument(long ownerId, Input input) {
        Assessment supported = null;
        boolean disagreement = false;
        for (HistoryEvidence source : input.evidence()) {
            String document = source.passage();
            int start = 0;
            while (start < document.length()) {
                int end = Math.min(document.length(), start + DOCUMENT_BATCH_CHARS);
                if (end < document.length()) {
                    int paragraph = document.lastIndexOf('\n', end);
                    if (paragraph > start + DOCUMENT_BATCH_CHARS / 2) {
                        end = paragraph;
                    }
                }
                String section = document.substring(start, end);
                HistoryEvidence part = new HistoryEvidence(source.institution(), source.sourceId(),
                        source.title(), source.sourceUrl(), source.status(), section);
                List<Assessment> results = assessBatch(ownerId,
                        List.of(new Input(input.index(), input.claim(), List.of(part))));
                if (results.size() != 1 || results.getFirst().index() != input.index()
                        || results.getFirst().status() == Status.LOOKUP_FAILED) {
                    throw invalidOutput();
                }
                Assessment next = results.getFirst();
                if (next.status() != Status.INSUFFICIENT_EVIDENCE) {
                    if (next.citation().isBlank() || !section.contains(next.citation())) {
                        disagreement = true;
                    } else if (supported == null) {
                        supported = next;
                    } else if (supported.status() != next.status() || !supported.eventYear().equals(next.eventYear())) {
                        disagreement = true;
                    }
                }
                // 최대 500자 인용문이 구간 경계에 걸려도 다음 요청에 포함한다.
                start = end == document.length() ? end : end - 500;
            }
        }
        if (supported != null && !disagreement) {
            return supported;
        }
        return new Assessment(input.index(), Status.INSUFFICIENT_EVIDENCE,
                disagreement ? "자료의 판정과 인용을 일관되게 확인할 수 없습니다." : "검토한 자료에서 이 주장을 직접 대조할 문장을 찾지 못했습니다.", "", 0);
    }

    private <T> T call(long ownerId, String prompt, Class<T> type) {
        quotaService.chargeGeminiText(ownerId);
        String responseJson = geminiClient.generateHistoryJson(prompt);
        try {
            T output = objectMapper.readValue(responseJson, type);
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
