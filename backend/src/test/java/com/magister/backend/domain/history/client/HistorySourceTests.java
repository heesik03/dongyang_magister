package com.magister.backend.domain.history.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.domain.history.error.HistoryErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

class HistorySourceTests {
    private final RestClient.Builder builder =
            RestClient.builder().baseUrl("https://fixture.example");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HeritageClient heritage = new HeritageClient(builder.build());
    private final EncyKoreaClient encyclopedia =
            new EncyKoreaClient(new ObjectMapper(), "test", builder.build());

    @Test
    void heritageRanksPalaceBeforeBuilding() {
        String identifiers =
                "<ccbaKdcd>13</ccbaKdcd><ccbaAsno>0001170000000</ccbaAsno><ccbaCtcd>11</ccbaCtcd>";
        String xml =
                "<result><totalCnt>2</totalCnt><item>"
                        + identifiers
                        + "<ccbaMnm1>경복궁 태원전</ccbaMnm1></item><item>"
                        + identifiers
                        + "<ccbaMnm1>경복궁</ccbaMnm1></item></result>";
        server.expect(requestTo(containsString("SearchKindOpenapiList.do")))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
        assertThat(heritage.candidates("경복궁").getFirst().title()).isEqualTo("경복궁");
    }

    @Test
    void heritageReturnsOriginalSentenceAndOfficialLink() {
        String xml =
                """
                <result><ccbaKdcd>13</ccbaKdcd><ccbaAsno>0001170000000</ccbaAsno><ccbaCtcd>11</ccbaCtcd>
                <ccbaCpno>1331101170000</ccbaCpno><item><ccbaMnm1>경복궁</ccbaMnm1>
                <content>태조 4년(1395)에 세웠다. 이후 중건되었다.</content></item></result>""";
        server.expect(requestTo(containsString("ccbaAsno=0001170000000")))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
        HistoryEvidence evidence = heritage.evidence("13-0001170000000-11", "1395");
        assertThat(evidence.passage()).isEqualTo("태조 4년(1395)에 세웠다.");
        assertThat(evidence.sourceUrl().getHost()).isEqualTo("www.heritage.go.kr");
    }

    @Test
    void encyclopediaSelectsBodyOfRequestedArticleOnly() {
        String body =
                """
                {"eid":"E0076893","headword":"경복궁 태원전","body":"# 개설\\n고종 때 건립하였다."}""";
        server.expect(requestTo("https://fixture.example/E0076893"))
                .andExpect(header("X-API-Key", "test"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        HistoryEvidence result = encyclopedia.evidence("E0076893", "고종");
        assertThat(result.passage()).isEqualTo("고종 때 건립하였다.");
        assertThat(result.title()).isEqualTo("경복궁 태원전");
    }

    @Test
    void missingPassagePreservesSourceIdentity() {
        server.expect(requestTo("https://fixture.example/E0076893"))
                .andRespond(
                        withSuccess(
                                "{\"eid\":\"E0076893\",\"headword\":\"경복궁 태원전\",\"body\":\"고종 때 건립하였다.\"}",
                                MediaType.APPLICATION_JSON));
        HistoryEvidence result = encyclopedia.evidence("E0076893", "1395");
        assertThat(result.status()).isEqualTo(HistoryEvidence.Status.NO_PASSAGE);
        assertThat(result.passage()).isNull();
    }

    @Test
    void emptySearchAndUnavailableProviderAreDistinct() {
        server.expect(requestTo(containsString("/search?")))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/search?")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(encyclopedia.candidates("경북궁")).isEmpty();
        assertThatThrownBy(() -> encyclopedia.candidates("경복궁"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(HistoryErrorCode.PROVIDER_LOOKUP_FAILED));
    }
}
