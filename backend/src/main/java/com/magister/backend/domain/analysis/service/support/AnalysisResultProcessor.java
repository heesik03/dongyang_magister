package com.magister.backend.domain.analysis.service.support;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.converter.AnalysisPromptConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.AnalysisOutput;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Candidate;
import com.magister.backend.domain.analysis.dto.AnalysisOutput.Evidence;
import com.magister.backend.domain.analysis.dto.ValidatedAnalysis;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 공급자 프롬프트·응답 규격과 근거 검증 정책을 함께 관리한다. */
@Component
@RequiredArgsConstructor
public class AnalysisResultProcessor {
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private static final String PROMPT_TEMPLATE = loadPrompt("prompts/analysis-review.txt") + "\n";
    private static final String TASK_BOTH = loadPrompt("prompts/analysis-task-both.txt");
    private static final String TASK_CONSISTENCY =
            loadPrompt("prompts/analysis-task-consistency.txt");
    private static final String TASK_MOTIVATION =
            loadPrompt("prompts/analysis-task-motivation.txt");
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AnalysisConverter resultConverter;
    private final AnalysisPromptConverter promptConverter;

    /**
     * JSON 구조와 크기를 확인하고 인용문·설정 ID·요청한 검토 종류가 실제 입력과 맞는지 검증한다. 위치·적용 범위가 없던 과거 판단은 새 후보에 자동 복사하지
     * 않는다. 검토 후보가 없는 빈 reviews는 정상 결과이며 잘못된 자료는 빈 결과로 숨기지 않는다.
     */
    public AnalysisData validate(String raw, AnalysisInput input) {
        return validateDetailed(raw, input).result();
    }

    /** 전체 후보의 UTF-16 반열린 구간과 출처를 검증하며 일부 잘못된 후보를 조용히 제외하지 않는다. */
    public ValidatedAnalysis validateDetailed(String raw, AnalysisInput input) {
        AnalysisOutput output = parse(raw);
        Map<String, String> sources = new HashMap<>();
        for (SettingReference setting : input.settings()) {
            sources.put("SETTING:" + setting.id(), setting.content());
        }
        for (ChapterData chapter : input.previous()) {
            sources.put("PREVIOUS:" + chapter.id(), chapter.content());
        }
        List<ReviewData> reviews = new ArrayList<>();
        for (Candidate candidate : output.reviews()) {
            verifyRange(
                    input.chapter().content(),
                    candidate.quote(),
                    candidate.startOffset(),
                    candidate.endOffset());
            if ((!input.consistency() && candidate.type().equals("설정 충돌 후보"))
                    || (!input.motivation() && candidate.type().equals("설명 보완"))) {
                invalid();
            }
            for (Evidence evidence : candidate.evidences()) {
                verifyRange(
                        sources.get(evidence.sourceType() + ":" + evidence.sourceKey()),
                        evidence.quote(),
                        evidence.startOffset(),
                        evidence.endOffset());
            }
            // 새 후보는 미검토로 시작한다. 범위가 있는 저장 판단도 입력의 참고 자료로만 사용한다.
            reviews.add(resultConverter.toReview(candidate));
        }
        return new ValidatedAnalysis(resultConverter.toResult(input, reviews), output.reviews());
    }

    private void verifyRange(String source, String quote, int start, int end) {
        if (source == null
                || start < 0
                || end <= start
                || end > source.length()
                || !source.substring(start, end).equals(quote)
                || splitsSurrogate(source, start)
                || splitsSurrogate(source, end)) {
            invalid();
        }
    }

    private boolean splitsSurrogate(String source, int offset) {
        return offset > 0
                && offset < source.length()
                && Character.isHighSurrogate(source.charAt(offset - 1))
                && Character.isLowSurrogate(source.charAt(offset));
    }

    /** 작품 자료를 명령과 구분하고 허용된 출력 구조를 명시한다. */
    public String buildPrompt(AnalysisInput input) {
        String exampleType = input.consistency() ? "설정 충돌 후보" : "설명 보완";
        String task =
                input.consistency() && input.motivation()
                        ? TASK_BOTH
                        : input.consistency() ? TASK_CONSISTENCY : TASK_MOTIVATION;
        return PROMPT_TEMPLATE.formatted(task, exampleType)
                + objectMapper.writeValueAsString(promptConverter.toPayload(input));
    }

    /** HTTP로 노출되는 static 경로 대신 클래스패스 전용 프롬프트 리소스를 UTF-8로 읽고 줄바꿈을 통일한다. */
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

    /** 역직렬화·중첩 항목 검증 실패를 공급자 응답 오류로 통일하며 원본 응답은 노출하지 않는다. */
    private AnalysisOutput parse(String raw) {
        try {
            if (raw == null || raw.length() > MAX_RESPONSE_CHARS) {
                throw new IllegalArgumentException();
            }

            AnalysisOutput output = objectMapper.readValue(raw, AnalysisOutput.class);
            if (!validator.validate(output).isEmpty()) {
                throw new IllegalArgumentException();
            }

            return output;
        } catch (RuntimeException error) {
            throw new CustomException(GeminiErrorCode.INVALID_RESPONSE);
        }
    }

    private void invalid() {
        throw new CustomException(GeminiErrorCode.INVALID_RESPONSE);
    }
}
