package com.magister.backend.domain.history.controller;

import com.magister.backend.domain.history.client.EncyKoreaClient;
import com.magister.backend.domain.history.client.HeritageClient;
import com.magister.backend.domain.history.client.HistorySource;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryProvider;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.domain.history.docs.HistoryEvidenceControllerDocs;
import com.magister.backend.global.response.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/history/{provider}")
public class HistoryEvidenceController implements HistoryEvidenceControllerDocs {

    private final EncyKoreaClient encyclopedia;
    private final HeritageClient heritage;

    @Override
    @GetMapping("/candidates")
    public ApiResult<List<HistoryCandidate>> candidates(@PathVariable HistoryProvider provider,
            @RequestParam String q) {
        return ApiResult.success(source(provider).candidates(q.strip()));
    }

    @Override
    @GetMapping("/evidence/{sourceId}")
    public ApiResult<HistoryEvidence> evidence(@PathVariable HistoryProvider provider,
            @PathVariable String sourceId,
            @RequestParam String keyword) {
        return ApiResult.success(source(provider).evidence(sourceId, keyword.strip()));
    }

    private HistorySource source(HistoryProvider provider) {
        return switch (provider) {
            case ENCYCLOPEDIA -> encyclopedia;
            case HERITAGE -> heritage;
        };
    }
}
