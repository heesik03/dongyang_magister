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

import java.net.URI;
import java.util.List;

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
    void documentKeepsDatesAndFullDescriptionEvenWhenTheKeywordIsAbsent() {
        String description = "궁 이름의 유래. ".repeat(120) + "1395년에 창건하였다.";
        String xml = """
                <result><ccbaKdcd>13</ccbaKdcd><ccbaAsno>0001170000000</ccbaAsno><ccbaCtcd>11</ccbaCtcd>
                <ccbaCpno>1331101170000</ccbaCpno><item><ccbaMnm1>경복궁</ccbaMnm1>
                <ccceName>조선 태조 4년</ccceName><ccbaAsdt>19630118</ccbaAsdt>
                <content>%s</content></item></result>""".formatted(description);
        server.expect(requestTo(containsString("ccbaAsno=0001170000000")))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
        assertThat(heritage.document("13-0001170000000-11").passage())
                .contains("시대\n조선 태조 4년", "지정일\n19630118", description);
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

    @Test
    void excerptDeprioritizesTargetTitleAndSeparatesQuotedSentences() {
        String content =
                "조선 왕조 제일의 법궁으로 태조 4년(1395) 한양으로 수도를 옮긴 후 처음으로 세운 궁궐이다. "
                        + "궁궐의 이름은 정도전이 『시경』에 나오는 “이미 술에 취하고 이미 덕에 배부르니 군자만년 그대의 큰 복을 도우리라”에서 "
                        + "큰 복을 빈다는 뜻의 ‘경복(景福)’이라는 두 글자를 따서 지은 것이다. "
                        + "1412년 태종은 경복궁의 연못을 크게 넓히고 섬 위에 경회루를 만들었다.";
        HistoryEvidence evidence =
                HistorySource.excerpt(
                        "국가유산청",
                        "13-0001170000000-11",
                        "경복궁",
                        URI.create("https://www.heritage.go.kr/"),
                        List.of("경복궁", "창건", "처음으로 세운", "건립"),
                        content);
        assertThat(evidence.status()).isEqualTo(HistoryEvidence.Status.FOUND);
        assertThat(evidence.passage())
                .isEqualTo("조선 왕조 제일의 법궁으로 태조 4년(1395) 한양으로 수도를 옮긴 후 처음으로 세운 궁궐이다.");
    }

    @Test
    void excerptSelectsHighestKeywordDensityAndBundlesNeighborContextForGeneralClaims() {
        String article =
                "조선 중기의 무신이다. 1591년 전라좌도수군절도사에 임명되었다. "
                        + "1592년 한산도 앞바다에서 학익진을 펼쳐 왜군을 크게 격파하였다.";
        HistoryEvidence evidence =
                HistorySource.excerpt(
                        "한국학중앙연구원",
                        "E0044760",
                        "이순신",
                        URI.create("https://encykorea.aks.ac.kr/Article/E0044760"),
                        List.of("이순신", "한산도", "학익진", "전라좌도수군절도사"),
                        article);
        assertThat(evidence.status()).isEqualTo(HistoryEvidence.Status.FOUND);
        assertThat(evidence.passage())
                .contains("1591년 전라좌도수군절도사에 임명되었다.")
                .contains("1592년 한산도 앞바다에서 학익진을 펼쳐 왜군을 크게 격파하였다.");
    }
}
