package com.magister.backend.domain.history.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

final class HistoryHttp {
    private static final int MAX_BYTES = 1024 * 1024;

    private HistoryHttp() {}

    static RestClient client(String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    static byte[] read(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange((sent, response) -> {
                if (response.getStatusCode().value() == 204 || response.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                }
                byte[] body = response.getBody().readNBytes(MAX_BYTES + 1);
                if (body.length == 0 || body.length > MAX_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                }
                return body;
            });
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
    }
}
