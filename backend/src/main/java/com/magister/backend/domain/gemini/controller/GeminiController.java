package com.magister.backend.domain.gemini.controller;

import com.magister.backend.domain.gemini.docs.GeminiControllerDocs;
import com.magister.backend.domain.gemini.dto.GeminiRequest;
import com.magister.backend.domain.gemini.dto.GeminiResponse;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.global.response.ApiResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gemini")
public class GeminiController implements GeminiControllerDocs {

    private final GeminiService geminiService;

    @Override
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GeminiResponse> generate(@Valid @RequestBody GeminiRequest request) {
        return ApiResponse.success(geminiService.generate(request));
    }
}
