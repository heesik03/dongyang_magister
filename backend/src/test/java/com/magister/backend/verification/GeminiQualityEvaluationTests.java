package com.magister.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.analysis.dto.ValidatedAnalysis;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.decision.dto.DecisionReference;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;
import com.magister.backend.global.gemini.GeminiProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** 일반 test에서는 실행하지 않는다. 실제 작품·계정 자료는 읽지 않고 아래 합성 자료만 전송한다. */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:gemini-quality;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MAGISTER_RUN_GEMINI_TESTS", matches = "true")
class GeminiQualityEvaluationTests {
    @Autowired GeminiClient geminiClient;
    @Autowired GeminiProperties geminiProperties;
    @Autowired AnalysisResultProcessor resultProcessor;
    @Autowired ObjectMapper objectMapper;

    private record Scenario(
            String name,
            AnalysisInput input,
            String expectedType,
            String expectedMarker,
            boolean rejectMagicCandidate) {}

    private record Observation(
            String name,
            long milliseconds,
            boolean contractValid,
            boolean expectationMatched,
            String errorCode,
            String raw,
            ValidatedAnalysis validated) {}

    @DynamicPropertySource
    static void realProvider(DynamicPropertyRegistry registry) throws Exception {
        Properties configuration = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (Reader reader = Files.newBufferedReader(Path.of(".env"))) {
                configuration.load(reader);
            }
        }
        String key = System.getenv("GEMINI_API_KEY");
        String model = System.getenv("GEMINI_MODEL");
        String actualKey = key == null ? configuration.getProperty("GEMINI_API_KEY", "") : key;
        String actualModel = model == null ? configuration.getProperty("GEMINI_MODEL", "") : model;
        registry.add("gemini.api-key", () -> actualKey);
        registry.add("gemini.model", () -> actualModel);
    }

    private AnalysisInput input(
            String chapter,
            String content,
            List<SettingReference> settings,
            List<DecisionReference> references,
            boolean consistency,
            boolean motivation) {
        return new AnalysisInput(
                new ChapterData(chapter, "합성 검증 회차", content, 1),
                settings,
                List.of(),
                List.of(),
                consistency,
                motivation,
                references);
    }

    @Test
    void evaluatesSyntheticInputsAndWritesMeasuredResults() throws Exception {
        SettingReference ability =
                new SettingReference("1", "주인공 능력", "주인공은 마법을 사용할 수 없다.", "인물", "확정", 0);
        SettingReference goal =
                new SettingReference("2", "주인공 목표", "주인공은 친구의 생명을 가장 소중히 여긴다.", "인물", "확정", 0);
        SettingReference other =
                new SettingReference("3", "동료 능력", "동료는 물속에서 숨을 쉴 수 없다.", "인물", "확정", 0);
        DecisionReference intent =
                new DecisionReference(
                        1L,
                        1L,
                        "synthetic-review",
                        1,
                        "의도된 설정",
                        "2화에서 봉인이 풀려 주인공이 마법을 얻었다.",
                        "a".repeat(64),
                        "주인공은 마법을 사용했다.",
                        "CHAPTER_RANGE",
                        "c2",
                        "c3",
                        null,
                        null,
                        null,
                        "봉인이 풀린 이후 주인공의 마법 능력에만 적용하며 동료의 능력에는 적용하지 않는다.",
                        List.of("주인공 능력 · 주인공은 마법을 사용할 수 없다."),
                        "2화",
                        "3화");
        List<Scenario> scenarios =
                List.of(
                        new Scenario(
                                "normal",
                                input(
                                        "c1",
                                        "주인공은 마법을 사용하지 않았다.",
                                        List.of(ability),
                                        List.of(),
                                        true,
                                        true),
                                null,
                                "",
                                false),
                        new Scenario(
                                "setting-conflict",
                                input(
                                        "c2",
                                        "주인공은 마법을 사용했다.",
                                        List.of(ability),
                                        List.of(),
                                        true,
                                        false),
                                "설정 충돌 후보",
                                "마법",
                                false),
                        new Scenario(
                                "motivation-gap",
                                input(
                                        "c2",
                                        "주인공은 아무 이유 없이 친구를 일부러 죽였다.",
                                        List.of(goal),
                                        List.of(),
                                        false,
                                        true),
                                "설명 보완",
                                "친구",
                                false),
                        new Scenario(
                                "explicit-twist",
                                input(
                                        "c2",
                                        "봉인이 풀리자 주인공은 마법을 사용했다.",
                                        List.of(
                                                new SettingReference(
                                                        "1",
                                                        "능력 조건",
                                                        "주인공은 봉인이 풀리기 전까지 마법을 사용할 수 없다.",
                                                        "인물",
                                                        "확정",
                                                        0)),
                                        List.of(),
                                        true,
                                        true),
                                null,
                                "",
                                false),
                        new Scenario(
                                "insufficient-evidence",
                                input(
                                        "c2",
                                        "주인공은 방으로 걸어갔다.",
                                        List.of(ability),
                                        List.of(),
                                        true,
                                        true),
                                null,
                                "",
                                false),
                        new Scenario(
                                "intent-inside-scope",
                                input(
                                        "c3",
                                        "주인공은 마법을 사용했다.",
                                        List.of(ability),
                                        List.of(intent),
                                        true,
                                        true),
                                null,
                                "",
                                false),
                        new Scenario(
                                "intent-unrelated-conflict",
                                input(
                                        "c3",
                                        "주인공은 마법을 사용했다.\n동료는 물속에서 숨을 쉬었다.",
                                        List.of(ability, other),
                                        List.of(intent),
                                        true,
                                        false),
                                "설정 충돌 후보",
                                "숨",
                                true),
                        new Scenario(
                                "intent-outside-scope",
                                input(
                                        "c4",
                                        "주인공은 마법을 사용했다.",
                                        List.of(ability),
                                        List.of(),
                                        true,
                                        false),
                                "설정 충돌 후보",
                                "마법",
                                false),
                        new Scenario(
                                "condition-ended-paraphrase",
                                input(
                                        "c2",
                                        "복도 조명이 켜진 뒤 센서가 작동했다.",
                                        List.of(
                                                new SettingReference(
                                                        "4",
                                                        "센서 동작 조건",
                                                        "복도 조명이 꺼져 있는 동안 센서는 작동하지 않는다.",
                                                        "세계관",
                                                        "확정",
                                                        0)),
                                        List.of(),
                                        true,
                                        true),
                                null,
                                "",
                                false),
                        new Scenario(
                                "condition-still-active",
                                input(
                                        "c2",
                                        "복도 조명은 꺼져 있었다. 센서가 작동했다.",
                                        List.of(
                                                new SettingReference(
                                                        "4",
                                                        "센서 동작 조건",
                                                        "복도 조명이 꺼져 있는 동안 센서는 작동하지 않는다.",
                                                        "세계관",
                                                        "확정",
                                                        0)),
                                        List.of(),
                                        true,
                                        false),
                                "설정 충돌 후보",
                                "센서",
                                false));
        assertThat(scenarios).hasSizeLessThanOrEqualTo(10);
        String selectedNames = System.getenv("MAGISTER_GEMINI_SCENARIOS");
        if (selectedNames != null && !selectedNames.isBlank()) {
            Set<String> requested =
                    Arrays.stream(selectedNames.split(","))
                            .map(String::strip)
                            .collect(Collectors.toSet());
            Set<String> known = scenarios.stream().map(Scenario::name).collect(Collectors.toSet());
            assertThat(known).containsAll(requested);
            scenarios =
                    scenarios.stream()
                            .filter(scenario -> requested.contains(scenario.name()))
                            .toList();
        }
        List<Observation> observations = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            long started = System.nanoTime();
            String raw = null;
            try {
                raw = geminiClient.generateAnalysis(resultProcessor.buildPrompt(scenario.input()));
                ValidatedAnalysis validated =
                        resultProcessor.validateDetailed(raw, scenario.input());
                boolean matched =
                        scenario.expectedType() == null
                                ? validated.candidates().isEmpty()
                                : validated.candidates().stream()
                                        .anyMatch(
                                                candidate ->
                                                        candidate
                                                                        .type()
                                                                        .equals(
                                                                                scenario
                                                                                        .expectedType())
                                                                && candidate
                                                                        .quote()
                                                                        .contains(
                                                                                scenario
                                                                                        .expectedMarker()));
                if (scenario.rejectMagicCandidate()
                        && validated.candidates().stream()
                                .anyMatch(candidate -> candidate.quote().contains("마법"))) {
                    matched = false;
                }
                observations.add(
                        new Observation(
                                scenario.name(),
                                (System.nanoTime() - started) / 1_000_000,
                                true,
                                matched,
                                null,
                                raw,
                                validated));
                System.out.println(
                        "GEMINI_EVALUATION "
                                + scenario.name()
                                + " contract=true expectation="
                                + matched);
            } catch (CustomException error) {
                String code = error.getErrorCode().getCode();
                observations.add(
                        new Observation(
                                scenario.name(),
                                (System.nanoTime() - started) / 1_000_000,
                                false,
                                false,
                                code,
                                raw,
                                null));
                System.out.println("GEMINI_EVALUATION " + scenario.name() + " error=" + code);
                if (!code.equals("INVALID_RESPONSE")) {
                    break;
                }
            }
        }
        Path report = Path.of("build/reports/gemini-evaluation.json");
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                Map.of(
                                        "evaluatedAt",
                                        Instant.now().toString(),
                                        "model",
                                        geminiProperties.model(),
                                        "maximumCalls",
                                        10,
                                        "plannedScenarios",
                                        scenarios.size(),
                                        "generationProfile",
                                        "analysis-schema-high-on-gemini3",
                                        "maxOutputTokens",
                                        8192,
                                        "observations",
                                        observations)));
        assertThat(observations).hasSize(scenarios.size());
        assertThat(observations).allMatch(Observation::contractValid);
        assertThat(observations).allMatch(Observation::expectationMatched);
    }
}
