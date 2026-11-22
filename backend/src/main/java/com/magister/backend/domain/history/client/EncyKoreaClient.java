package com.magister.backend.domain.history.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Profile("!prod")
@Component
public class EncyKoreaClient {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public EncyKoreaClient(ObjectMapper objectMapper, @Value("${ENCYKOREA_API_KEY:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey.strip();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl("https://devin.aks.ac.kr:8080/api/articles")
                .requestFactory(requestFactory)
                .build();
    }

    public JsonNode search(String keyword) {
        return fetch("/search?q={keyword}&p=1&ps=5", keyword);
    }

    public JsonNode detail(String eid) {
        return fetch("/{eid}", eid);
    }

    private JsonNode fetch(String path, String value) {
        if (apiKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            return restClient.get().uri(path, value)
                    .header("X-API-Key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                        }
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                        }
                        JsonNode result = objectMapper.readTree(body);
                        if (result == null || !(result.isArray() || result.isObject())) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                        }
                        return result;
                    });
        } catch (RestClientException | JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
    }
}
