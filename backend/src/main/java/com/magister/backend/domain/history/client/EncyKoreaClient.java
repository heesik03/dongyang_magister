package com.magister.backend.domain.history.client;

import com.magister.backend.domain.history.converter.HistoryConverter;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.domain.history.error.HistoryErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class EncyKoreaClient implements HistorySource {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public EncyKoreaClient(
            ObjectMapper objectMapper, @Value("${ENCYKOREA_API_KEY:}") String apiKey) {
        this(objectMapper, apiKey, HistoryHttp.client("https://devin.aks.ac.kr:8080/api/articles"));
    }

    EncyKoreaClient(ObjectMapper objectMapper, String apiKey, RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey.strip();
    }

    public JsonNode search(String keyword) {
        return fetch("/search?q={keyword}&p=1&ps=5", keyword);
    }

    public JsonNode detail(String eid) {
        return fetch("/{eid}", eid);
    }

    @Override
    public List<HistoryCandidate> candidates(String query) {
        JsonNode items = search(query).path("items");
        if (!items.isArray()) {
            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
        }
        List<HistoryCandidate> candidates = new ArrayList<>();
        for (JsonNode item : items) {
            String id = required(item, "eid");
            if (!id.matches("E[0-9]{7}")) {
                throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
            }
            candidates.add(
                    HistoryConverter.toCandidate(
                            id, required(item, "headword"), field(item, "definition")));
        }
        return HistorySource.ranked(candidates, query);
    }

    @Override
    public HistoryEvidence evidence(String sourceId, List<String> keywords) {
        if (!sourceId.matches("E[0-9]{7}")) {
            throw new CustomException(HistoryErrorCode.INVALID_SOURCE_ID);
        }
        JsonNode article = detail(sourceId);
        if (!sourceId.equals(required(article, "eid"))) {
            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
        }
        return HistorySource.excerpt(
                "한국학중앙연구원",
                sourceId,
                required(article, "headword"),
                URI.create("https://encykorea.aks.ac.kr/Article/" + sourceId),
                keywords,
                field(article, "body"),
                field(article, "summary"),
                field(article, "definition"));
    }

    private static String field(JsonNode article, String name) {
        return article.path(name).asString("").strip();
    }

    private static String required(JsonNode article, String name) {
        String value = field(article, name);
        if (!article.path(name).isString() || value.isEmpty()) {
            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
        }
        return value;
    }

    private JsonNode fetch(String path, String value) {
        if (apiKey.isEmpty()) {
            throw new CustomException(HistoryErrorCode.PROVIDER_KEY_MISSING);
        }
        byte[] body =
                HistoryHttp.read(
                        restClient
                                .get()
                                .uri(path, value)
                                .header("X-API-Key", apiKey)
                                .accept(MediaType.APPLICATION_JSON));
        try {
            JsonNode result = objectMapper.readTree(body);
            if (result == null || !(result.isObject() || result.isArray())) {
                throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
            }
            return result;
        } catch (JacksonException exception) {
            throw new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
        }
    }
}
