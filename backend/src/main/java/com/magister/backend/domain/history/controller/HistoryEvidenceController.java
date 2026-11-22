package com.magister.backend.domain.history.controller;

import com.magister.backend.domain.history.client.EncyKoreaClient;
import com.magister.backend.domain.history.client.HeritageClient;
import com.magister.backend.domain.history.client.HistorySource;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Tag(name = "역사 고증 자료", description = "로그인 세션 필요. 백과사전은 ENCYCLOPEDIA, 국가유산청은 HERITAGE를 선택합니다.")
public class HistoryEvidenceController {
    public enum Provider { ENCYCLOPEDIA, HERITAGE }

    private final EncyKoreaClient encyclopedia;
    private final HeritageClient heritage;

    @GetMapping("/candidates")
    @Operation(summary = "공식 자료 후보 검색", description = "q 예시: 경복궁. 첫 페이지에서 정확한 이름을 우선해 최대 10건을 반환합니다. 검색 결과가 없으면 빈 배열입니다.")
    public ApiResult<List<HistoryCandidate>> candidates(@PathVariable Provider provider,
            @RequestParam @NotBlank @Size(max = 100) String q) {
        return ApiResult.success(source(provider).candidates(q.strip()));
    }

    @GetMapping("/evidence/{sourceId}")
    @Operation(summary = "선택한 자료의 근거 대목 조회", description = "후보의 sourceId와 원문 검색어(예: 1395)를 입력합니다. 첫 일치 문장을 최대 500자로 발췌하며, 일치 대목이 없으면 NO_PASSAGE입니다. 자료 조회 실패는 502, 키 미설정은 503입니다.")
    public ApiResult<HistoryEvidence> evidence(@PathVariable Provider provider,
            @PathVariable @Size(max = 64) String sourceId,
            @RequestParam @NotBlank @Size(max = 100) String keyword) {
        return ApiResult.success(source(provider).evidence(sourceId, keyword.strip()));
    }

    private HistorySource source(Provider provider) {
        return switch (provider) {
            case ENCYCLOPEDIA -> encyclopedia;
            case HERITAGE -> heritage;
        };
    }
}
