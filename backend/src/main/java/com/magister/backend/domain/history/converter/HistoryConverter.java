package com.magister.backend.domain.history.converter;

import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryCheck.Claim;
import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import com.magister.backend.domain.history.dto.HistoryCheck.Status;
import com.magister.backend.domain.history.dto.HistoryEvidence;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** 외부 역사 기관 응답과 주장 대조 결과를 도메인 DTO로 조립한다. */
@Component
public class HistoryConverter {
    private HistoryConverter() {}

    public static HistoryCandidate toCandidate(String sourceId, String title, String description) {
        return new HistoryCandidate(sourceId, title, description);
    }

    public static HistoryEvidence toFoundEvidence(
            String institution, String sourceId, String title, URI sourceUrl, String passage) {
        return new HistoryEvidence(
                institution, sourceId, title, sourceUrl, HistoryEvidence.Status.FOUND, passage);
    }

    public static HistoryEvidence toMissingPassageEvidence(
            String institution, String sourceId, String title, URI sourceUrl) {
        return new HistoryEvidence(
                institution, sourceId, title, sourceUrl, HistoryEvidence.Status.NO_PASSAGE, null);
    }

    public static Finding toFinding(
            Claim claim,
            int startOffset,
            Status status,
            String reason,
            List<HistoryEvidence> evidence) {
        return new Finding(
                claim.quote(),
                startOffset,
                startOffset + claim.quote().length(),
                claim.sceneTime(),
                claim.target(),
                claim.assertion(),
                status,
                reason,
                List.copyOf(evidence));
    }

    public static Finding toAssessedFinding(
            Finding original, Status status, @Nullable String reason) {
        return new Finding(
                original.quote(),
                original.startOffset(),
                original.endOffset(),
                original.sceneTime(),
                original.target(),
                original.assertion(),
                status,
                reason == null ? "" : reason,
                original.evidence());
    }
}
