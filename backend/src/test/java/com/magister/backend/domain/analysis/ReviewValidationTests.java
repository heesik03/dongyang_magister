package com.magister.backend.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.converter.AnalysisPromptConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.global.error.exception.CustomException;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class ReviewValidationTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisResultProcessor processor =
            new AnalysisResultProcessor(
                    mapper,
                    FACTORY.getValidator(),
                    new AnalysisConverter(mapper),
                    new AnalysisPromptConverter());
    private final AnalysisInput input =
            new AnalysisInput(
                    new ChapterData("target", "2화", "😀장면\n마법 사용\n마법 사용", 1),
                    List.of(new SettingReference("1", "능력", "마법 불가", "인물", "확정", 0)),
                    List.of(new ChapterData("previous", "1화", "이전에는 마법 불가", 1)),
                    List.of(),
                    true,
                    true);

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private Map<String, Object> evidence(
            String type, String key, String quote, int start, int end) {
        return Map.of(
                "sourceType",
                type,
                "sourceKey",
                key,
                "quote",
                quote,
                "startOffset",
                start,
                "endOffset",
                end,
                "explanation",
                "능력 변화 확인");
    }

    private String raw(String quote, int start, int end, List<Map<String, Object>> evidences) {
        return mapper.writeValueAsString(
                Map.of(
                        "reviews",
                        List.of(
                                Map.of(
                                        "type",
                                        "설정 충돌 후보",
                                        "quote",
                                        quote,
                                        "startOffset",
                                        start,
                                        "endOffset",
                                        end,
                                        "evidences",
                                        evidences,
                                        "reason",
                                        "능력 획득 과정 확인 필요",
                                        "suggestion",
                                        "능력 변화 계기를 보완"))));
    }

    @Test
    void promptProvidesExactRepeatedSentencePositionsWithoutChangingSnapshot() {
        AnalysisPromptConverter converter = new AnalysisPromptConverter();
        assertThat(converter.passages(input.chapter().content()))
                .containsExactly(
                        new AnalysisPromptConverter.Passage(0, 4, "😀장면"),
                        new AnalysisPromptConverter.Passage(5, 10, "마법 사용"),
                        new AnalysisPromptConverter.Passage(11, 16, "마법 사용"));
        String prompt = processor.buildPrompt(input);
        assertThat(prompt).contains("\"startOffset\":11,\"endOffset\":16,\"quote\":\"마법 사용\"");
        assertThat(prompt).contains("\"settings\"", "\"previous\"", "motivation");
        assertThat(input.chapter().content()).isEqualTo("😀장면\n마법 사용\n마법 사용");
        String longSource = "가".repeat(1999) + "😀" + "나".repeat(2000) + "\r\n 끝. ";
        List<AnalysisPromptConverter.Passage> passages = converter.passages(longSource);
        assertThat(passages)
                .allSatisfy(
                        passage -> {
                            assertThat(passage.quote())
                                    .isEqualTo(
                                            longSource.substring(
                                                    passage.startOffset(), passage.endOffset()));
                            assertThat(passage.quote().length()).isBetween(1, 2000);
                            assertThat(Character.isLowSurrogate(passage.quote().charAt(0)))
                                    .isFalse();
                            assertThat(
                                            Character.isHighSurrogate(
                                                    passage.quote()
                                                            .charAt(passage.quote().length() - 1)))
                                    .isFalse();
                        });
        assertThat(passages).extracting(AnalysisPromptConverter.Passage::quote).contains("끝.");
    }

    @Test
    void repeatedQuoteKeepsSecondUtf16RangeAndMultipleEvidence() {
        int start = input.chapter().content().lastIndexOf("마법 사용");
        String raw =
                raw(
                        "마법 사용",
                        start,
                        start + 5,
                        List.of(
                                evidence("SETTING", "1", "마법 불가", 0, 5),
                                evidence("PREVIOUS", "previous", "마법 불가", 5, 10)));
        assertThat(processor.validateDetailed(raw, input).candidates().getFirst().startOffset())
                .isEqualTo(11);
        assertThat(processor.validateDetailed(raw, input).candidates().getFirst().evidences())
                .hasSize(2);
    }

    @Test
    void previousOnlyEvidenceDoesNotRequireFabricatedSetting() {
        String raw =
                raw("마법 사용", 11, 16, List.of(evidence("PREVIOUS", "previous", "마법 불가", 5, 10)));
        assertThat(processor.validate(raw, input).reviews().getFirst().settingId())
                .isEqualTo("previous:previous");
    }

    @Test
    void invalidSourceQuoteRangeAndSurrogateBoundaryAreRejected() {
        for (String raw :
                List.of(
                        raw("마법 사용", 10, 15, List.of(evidence("SETTING", "1", "마법 불가", 0, 5))),
                        raw("마법 사용", 11, 16, List.of(evidence("SETTING", "other", "마법 불가", 0, 5))),
                        raw(
                                "마법 사용",
                                11,
                                16,
                                List.of(evidence("PREVIOUS", "target", "마법 사용", 0, 5))),
                        raw("마법 사용", 11, 16, List.of(evidence("SETTING", "1", "마법 허용", 0, 5))),
                        raw("마법 사용", 11, 16, List.of(evidence("SETTING", "1", "마법 불가", -1, 4))),
                        raw("마법 사용", 11, 16, List.of()),
                        raw(
                                input.chapter().content().substring(1, 2),
                                1,
                                2,
                                List.of(evidence("SETTING", "1", "마법 불가", 0, 5))))) {
            assertThatThrownBy(() -> processor.validate(raw, input))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Test
    void disabledReviewTypeAndOldProviderSchemaAreRejected() {
        AnalysisInput motivationOnly =
                new AnalysisInput(
                        input.chapter(),
                        input.settings(),
                        input.previous(),
                        List.of(),
                        false,
                        true);
        assertThat(processor.buildPrompt(motivationOnly)).contains("\"type\":\"설명 보완\"");
        assertThatThrownBy(
                        () ->
                                processor.validate(
                                        raw(
                                                "마법 사용",
                                                11,
                                                16,
                                                List.of(evidence("SETTING", "1", "마법 불가", 0, 5))),
                                        motivationOnly))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(
                        () ->
                                processor.validate(
                                        "{\"reviews\":[{\"type\":\"설정 충돌 후보\",\"quote\":\"마법"
                                            + " 사용\",\"settingId\":\"1\",\"reason\":\"이유\",\"suggestion\":\"방향\"}]}",
                                        input))
                .isInstanceOf(CustomException.class);
        assertThat(processor.validate("{\"reviews\":[]}", input).reviews()).isEmpty();
    }
}
