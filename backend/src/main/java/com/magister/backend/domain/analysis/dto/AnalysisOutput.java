package com.magister.backend.domain.analysis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 공급자 응답 전용 경계 타입이며 검증 후 내부 검토 DTO로 변환한다. */
public record AnalysisOutput(@NotNull @Size(max = 100) List<@NotNull @Valid Candidate> reviews) {
    public record Candidate(
            @NotNull @Pattern(regexp = "설정 충돌 후보|설명 보완") String type,
            @NotBlank @Size(max = 10000) String quote,
            @NotNull @Min(0) Integer startOffset,
            @NotNull @Min(1) Integer endOffset,
            @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Evidence> evidences,
            @NotBlank @Size(max = 4000) String reason,
            @NotBlank @Size(max = 4000) String suggestion,
            @Size(max = 128) String issueGroupKey,
            @Size(max = 200) String issueGroupTitle,
            @Size(max = 4000) String confirmedFact,
            @Size(max = 4000) String interpretation,
            @Size(max = 4000) String missingInfo,
            @Size(min = 2, max = 5) List<@NotNull @Valid RevisionOption> options) {
        /** 기존 형식은 읽되 새 형식의 부분 응답을 사실·선택지로 노출하지 않는다. */
        @AssertTrue
        public boolean isStructuredContentComplete() {
            if (issueGroupKey == null && issueGroupTitle == null && confirmedFact == null
                    && interpretation == null && missingInfo == null && options == null) {
                return true;
            }
            return hasText(issueGroupKey) && hasText(issueGroupTitle) && hasText(confirmedFact)
                    && hasText(interpretation) && hasText(missingInfo) && options != null;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        public Candidate(
                String type,
                String quote,
                Integer startOffset,
                Integer endOffset,
                List<Evidence> evidences,
                String reason,
                String suggestion) {
            this(
                    type,
                    quote,
                    startOffset,
                    endOffset,
                    evidences,
                    reason,
                    suggestion,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public record Evidence(
            @NotNull @Pattern(regexp = "SETTING|PREVIOUS") String sourceType,
            @NotBlank @Size(max = 64) String sourceKey,
            @NotBlank @Size(max = 10000) String quote,
            @NotNull @Min(0) Integer startOffset,
            @NotNull @Min(1) Integer endOffset,
            @NotBlank @Size(max = 4000) String explanation) {}

    public record RevisionOption(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String direction,
            @NotBlank @Size(max = 2000) String premise,
            @NotBlank @Size(max = 2000) String rationale) {}
}
