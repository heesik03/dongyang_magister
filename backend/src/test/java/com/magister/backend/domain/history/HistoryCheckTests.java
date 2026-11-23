package com.magister.backend.domain.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.aiquota.AiQuotaService;
import com.magister.backend.domain.history.client.EncyKoreaClient;
import com.magister.backend.domain.history.client.HeritageClient;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.domain.history.error.HistoryErrorCode;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoryCheckTests {
    private static final AtomicLong IDS = new AtomicLong();
    private static final String PASSAGE = "태조 4년(1395)에 처음으로 세운 궁궐이다.";
    private static final HistoryCandidate PALACE =
            new HistoryCandidate("13-0001170000000-11", "경복궁", "사적");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean GeminiClient gemini;
    @MockitoBean AiQuotaService quota;
    @MockitoBean HeritageClient heritage;
    @MockitoBean EncyKoreaClient encyclopedia;
    private final long ownerId = IDS.incrementAndGet();

    private String quote(int year) {
        return year + "년, 사신은 경복궁에 도착했다.";
    }

    private String extraction(int year) {
        return """
                {"claims":[{"quote":"%s","occurrence":1,"target":"경복궁","sceneTime":"%d년",
                "assertion":"경복궁이 존재했다","kind":"EXISTENCE","keywords":["세운"]}]}
                """
                .formatted(quote(year), year);
    }

    private String assessment(String citation) {
        return """
                {"assessments":[{"index":0,"status":"POSSIBLE_CONFLICT","reason":"창건 시점 대조",
                "citation":"%s","eventYear":1395}]}
                """
                .formatted(citation);
    }

    private void palace() {
        when(heritage.candidates("경복궁")).thenReturn(List.of(PALACE));
        when(heritage.document(PALACE.sourceId()))
                .thenReturn(
                        new HistoryEvidence(
                                "국가유산청",
                                PALACE.sourceId(),
                                PALACE.title(),
                                URI.create("https://www.heritage.go.kr/"),
                                HistoryEvidence.Status.FOUND,
                                PASSAGE));
    }

    private ResultActions check(String manuscript) throws Exception {
        LoginUser author = new LoginUser(ownerId, "author@example.com", "작가");
        return mvc.perform(
                post("/api/history/check")
                        .with(csrf())
                        .with(
                                authentication(
                                        UsernamePasswordAuthenticationToken.authenticated(
                                                author, null, List.of())))
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("manuscript", manuscript))));
    }

    @ParameterizedTest
    @CsvSource({"1392,POSSIBLE_CONFLICT", "1396,NO_CONFLICT_FOUND"})
    void comparesVerifiedYearsAndReturnsOriginalOffsets(int year, String status) throws Exception {
        String prefix = "📜 원고\n";
        palace();
        when(gemini.generateHistoryJson(anyString()))
                .thenReturn(extraction(year), assessment(PASSAGE));
        check(prefix + quote(year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value(status))
                .andExpect(jsonPath("$.data[0].startOffset").value(prefix.length()))
                .andExpect(
                        jsonPath("$.data[0].endOffset").value(prefix.length() + quote(year).length()))
                .andExpect(jsonPath("$.data[0].evidence[0].passage").value(PASSAGE));
        verify(quota, times(2)).chargeGeminiText(ownerId);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(2)).generateHistoryJson(prompts.capture());
        assertThat(prompts.getAllValues().getFirst())
                .startsWith(template("history-claim-extract"))
                .contains(quote(year));
        assertThat(prompts.getAllValues().getLast())
                .startsWith(template("history-claim-assess"))
                .contains(PASSAGE);
    }

    @ParameterizedTest
    @CsvSource({"1959,POSSIBLE_CONFLICT", "1395,NO_CONFLICT_FOUND"})
    void comparesConstructionToSameEventRatherThanExistence(int year, String expected) throws Exception {
        palace();
        String sentence = year + "년 경복궁의 건설되었다.";
        String claim = extraction(year).replace(quote(year), sentence)
                .replace("존재했다", "건립되었다");
        when(gemini.generateHistoryJson(anyString())).thenReturn(claim, assessment(PASSAGE));
        check(sentence).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kind").value("FOUNDATION"))
                .andExpect(jsonPath("$.data[0].status").value(expected))
                .andExpect(jsonPath("$.data[0].officialYear").value(1395))
                .andExpect(jsonPath("$.data[0].citation").value(PASSAGE));
    }

    @Test
    void reconstructionUsesTheRebuildClauseInsteadOfTheFoundingYear() throws Exception {
        String passage = "1395년에 창건하고 1867년에 중건되었다.";
        when(heritage.candidates("경복궁")).thenReturn(List.of(PALACE));
        when(heritage.document(PALACE.sourceId())).thenReturn(new HistoryEvidence(
                "국가유산청", PALACE.sourceId(), PALACE.title(), URI.create("https://www.heritage.go.kr/"),
                HistoryEvidence.Status.FOUND, passage));
        String sentence = "1867년 경복궁이 중건되었다.";
        when(gemini.generateHistoryJson(anyString())).thenReturn(
                extraction(1867).replace(quote(1867), sentence).replace("EXISTENCE", "RECONSTRUCTION"),
                assessment(passage).replace("1395}", "1867}"));
        check(sentence).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("NO_CONFLICT_FOUND"))
                .andExpect(jsonPath("$.data[0].officialYear").value(1867));
    }

    @Test
    void readsLastSectionOfLongDocumentAndReportsPartialProviderFailure() throws Exception {
        String document = "궁궐의 이름을 지었다. ".repeat(2100) + PASSAGE;
        when(heritage.candidates("경복궁")).thenReturn(List.of(PALACE));
        when(heritage.document(PALACE.sourceId())).thenReturn(new HistoryEvidence(
                "국가유산청", PALACE.sourceId(), PALACE.title(), URI.create("https://www.heritage.go.kr/"),
                HistoryEvidence.Status.FOUND, document));
        when(encyclopedia.candidates("경복궁")).thenThrow(new CustomException(HistoryErrorCode.PROVIDER_KEY_MISSING));
        when(gemini.generateHistoryJson(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            if (prompt.contains("원고 JSON:")) {
                return extraction(1392);
            }
            return prompt.contains(PASSAGE) ? assessment(PASSAGE) : """
                    {"assessments":[{"index":0,"status":"INSUFFICIENT_EVIDENCE","reason":"연도 없음","citation":"","eventYear":0}]}
                    """;
        });
        check(quote(1392)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].citation").value(PASSAGE))
                .andExpect(jsonPath("$.data[0].evidence[0].passage").value(document))
                .andExpect(jsonPath("$.data[0].lookups[1].status").value("FAILED"));
        verify(heritage).document(PALACE.sourceId());
        verify(quota, times(3)).chargeGeminiText(ownerId);
    }

    @Test
    void unrelatedSceneYearDoesNotReachAssessment() throws Exception {
        String manuscript = "1392년 겨울이었다.\n\n사신은 경복궁에 도착했다.";
        when(gemini.generateHistoryJson(anyString())).thenReturn(extraction(1392)
                .replace(quote(1392), "사신은 경복궁에 도착했다."));
        check(manuscript).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("INSUFFICIENT_EVIDENCE"));
        verifyNoInteractions(heritage, encyclopedia);
    }

    private String template(String name) throws Exception {
        return new ClassPathResource("prompts/" + name + ".txt")
                .getContentAsString(StandardCharsets.UTF_8)
                .strip();
    }

    @Test
    void conflictingFoundationDatesWithholdFinding() throws Exception {
        palace();
        when(encyclopedia.candidates("경복궁"))
                .thenReturn(List.of(new HistoryCandidate("E0000001", "경복궁", "궁궐")));
        when(encyclopedia.document("E0000001"))
                .thenReturn(
                        new HistoryEvidence(
                                "한국학중앙연구원",
                                "E0000001",
                                "경복궁",
                                URI.create("https://encykorea.aks.ac.kr/"),
                                HistoryEvidence.Status.FOUND,
                                "1394년에 창건하였다."));
        when(gemini.generateHistoryJson(anyString()))
                .thenReturn(extraction(1392), assessment(PASSAGE));
        check(quote(1392))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("INSUFFICIENT_EVIDENCE"));
    }

    @Test
    void laterRebuildYearInSeparateSentenceDoesNotInvalidateFoundationYear() throws Exception {
        String passageWithRebuild = "태조 4년(1395)에 처음으로 세운 궁궐이다. 1867년에 중건되었다.";
        when(heritage.candidates("경복궁")).thenReturn(List.of(PALACE));
        when(heritage.document(PALACE.sourceId()))
                .thenReturn(
                        new HistoryEvidence(
                                "국가유산청",
                                PALACE.sourceId(),
                                PALACE.title(),
                                URI.create("https://www.heritage.go.kr/"),
                                HistoryEvidence.Status.FOUND,
                                passageWithRebuild));
        when(gemini.generateHistoryJson(anyString()))
                .thenReturn(extraction(1392), assessment(PASSAGE));
        check(quote(1392))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("POSSIBLE_CONFLICT"));
    }

    @Test
    void contextSceneTimeOutsideQuoteSentenceIsAccepted() throws Exception {
        palace();
        String manuscript = "1392년 겨울이었다.\n사신은 경복궁에 도착했다.";
        String contextExtraction =
                """
                {"claims":[{"quote":"사신은 경복궁에 도착했다.","occurrence":1,"target":"경복궁","sceneTime":"1392년",
                "assertion":"경복궁이 존재했다","kind":"EXISTENCE","keywords":["세운"]}]}
                """;
        when(gemini.generateHistoryJson(anyString()))
                .thenReturn(contextExtraction, assessment(PASSAGE));
        check(manuscript)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("POSSIBLE_CONFLICT"));
    }

    @Test
    void partialProviderFailureStillAssessesAvailableOfficialEvidence() throws Exception {
        palace();
        when(encyclopedia.candidates("경복궁"))
                .thenThrow(new CustomException(HistoryErrorCode.PROVIDER_KEY_MISSING));
        when(gemini.generateHistoryJson(anyString()))
                .thenReturn(extraction(1392), assessment(PASSAGE));
        check(quote(1392))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("POSSIBLE_CONFLICT"));
    }

    @Test
    void rejectsInventedSourceQuotation() throws Exception {
        palace();
        when(gemini.generateHistoryJson(anyString()))
                .thenReturn(extraction(1392), assessment("1395년 창건했다."));
        check(quote(1392))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("INSUFFICIENT_EVIDENCE"));
    }

    @Test
    void ambiguousIdentitySkipsJudgment() throws Exception {
        when(heritage.candidates("경복궁"))
                .thenReturn(
                        List.of(PALACE, new HistoryCandidate("second", "경복궁", "다른 대상")));
        when(gemini.generateHistoryJson(anyString())).thenReturn(extraction(1392));
        check(quote(1392))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("INSUFFICIENT_EVIDENCE"));
        verify(quota).chargeGeminiText(ownerId);
    }

    @Test
    void lookupFailureIsNotHistoricalConflict() throws Exception {
        when(heritage.candidates("경복궁"))
                .thenThrow(new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED));
        when(gemini.generateHistoryJson(anyString())).thenReturn(extraction(1392));
        check(quote(1392))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("LOOKUP_FAILED"));
    }

    @Test
    void fabricatedManuscriptQuoteStopsBeforeSearch() throws Exception {
        when(gemini.generateHistoryJson(anyString())).thenReturn(extraction(1396));
        check(quote(1392)).andExpect(status().isBadGateway());
        verifyNoInteractions(heritage, encyclopedia);
    }

    @Test
    void rejectsInvalidBodyAndMissingCsrf() throws Exception {
        check(" ").andExpect(status().isBadRequest());
        mvc.perform(post("/api/history/check").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(gemini, heritage, encyclopedia);
    }
}
