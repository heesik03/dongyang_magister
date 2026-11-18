package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RevisionSuggestionConverter {
    private static final int CONTEXT_CODE_POINTS = 300;
    private final ObjectMapper objectMapper;

    public RevisionSuggestion.Context toContext(ReviewCandidate candidate, List<ReviewEvidence> evidence) {
        AnalysisSource source = candidate.getTargetSource();
        String content = source.getContent();
        int start = candidate.getStartOffset();
        int end = candidate.getEndOffset();
        String quote = candidate.getQuote();
        if (start < 0 || end <= start || end > content.length() || !content.substring(start, end).equals(quote)) {
            throw new CustomException(AnalysisErrorCode.SNAPSHOT_UNAVAILABLE);
        }
        int left = content.offsetByCodePoints(start, -Math.min(CONTEXT_CODE_POINTS, content.codePointCount(0, start)));
        int right = content.offsetByCodePoints(end, Math.min(CONTEXT_CODE_POINTS, content.codePointCount(end, content.length())));
        List<RevisionSuggestion.Setting> settings = evidence.stream()
                .filter(item -> "SETTING".equals(item.getSource().getType()))
                .map(item -> new RevisionSuggestion.Setting(item.getSource().getTitle(), item.getQuote()))
                .toList();
        RevisionSuggestion.Material material = new RevisionSuggestion.Material(content.substring(left, start),
                quote, content.substring(end, right), candidate.getReason(), candidate.getSuggestion(), settings);
        RevisionSuggestion.Target target = new RevisionSuggestion.Target(source.getSourceKey(), source.getRevision(), start, end, quote);
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(List.of(candidate.getId(), target, material));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
            return new RevisionSuggestion.Context(target, material, hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public RevisionSuggestion.Result toResult(RevisionSuggestion.Target target, String replacement) {
        if (replacement == null || replacement.isBlank() || replacement.length() > 10000
                || replacement.strip().equals(target.quote())) {
            throw new CustomException(GeminiErrorCode.INVALID_RESPONSE);
        }
        return new RevisionSuggestion.Result(target, replacement.strip());
    }
}
