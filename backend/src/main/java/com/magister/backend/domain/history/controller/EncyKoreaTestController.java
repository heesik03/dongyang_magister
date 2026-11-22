package com.magister.backend.domain.history.controller;

import com.magister.backend.domain.history.client.EncyKoreaClient;
import com.magister.backend.global.response.ApiResult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

@Profile("!prod")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/history/test/articles")
public class EncyKoreaTestController {

    private final EncyKoreaClient client;

    @GetMapping
    public ApiResult<JsonNode> search(@RequestParam @NotBlank @Size(max = 100) String q) {
        return ApiResult.success(client.search(q.strip()));
    }

    @GetMapping("/{eid}")
    public ApiResult<JsonNode> detail(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String eid) {
        return ApiResult.success(client.detail(eid));
    }
}
