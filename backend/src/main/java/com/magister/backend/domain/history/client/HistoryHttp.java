package com.magister.backend.domain.history.client;

import com.magister.backend.domain.history.error.HistoryErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

final class HistoryHttp {
    private static final int MAX_BYTES = 1024 * 1024;

    private HistoryHttp() {}

    static RestClient client(String baseUrl) {
        // 해외 클라우드 리전에서 국내 공공 API(TLS·비표준 포트) 연결 지연을 견디도록 연결·읽기 시간 여유를 둔다.
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(12));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    static byte[] read(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange(
                    (sent, response) -> {
                        if (response.getStatusCode().value() == 204
                                || response.getStatusCode().value() == 404) {
                            throw new CustomException(HistoryErrorCode.SOURCE_NOT_FOUND);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
                        }
                        byte[] body = response.getBody().readNBytes(MAX_BYTES + 1);
                        if (body.length == 0 || body.length > MAX_BYTES) {
                            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
                        }
                        return body;
                    });
        } catch (RestClientException exception) {
            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
        }
    }
}
