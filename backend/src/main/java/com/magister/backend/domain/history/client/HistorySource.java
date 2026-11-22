package com.magister.backend.domain.history.client;

import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;

import java.net.URI;
import java.text.BreakIterator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public interface HistorySource {
    List<HistoryCandidate> candidates(String query);

    HistoryEvidence evidence(String sourceId, String keyword);

    static List<HistoryCandidate> ranked(List<HistoryCandidate> candidates, String query) {
        return candidates.stream()
                .sorted(Comparator.comparing(candidate -> !candidate.title().equals(query)))
                .limit(10)
                .toList();
    }

    static HistoryEvidence excerpt(String institution, String id, String title, URI url,
            String keyword, String... sections) {
        for (String line : Arrays.stream(sections).flatMap(String::lines).toList()) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.KOREAN);
            sentences.setText(line);
            int start = sentences.first();
            for (int end = sentences.next(); end != BreakIterator.DONE; end = sentences.next()) {
                String sentence = line.substring(start, end).strip();
                int match = sentence.indexOf(keyword);
                if (match >= 0 && !sentence.isEmpty()) {
                    int from = Math.max(0, match - 160);
                    String passage = sentence.substring(from, Math.min(sentence.length(), from + 500));
                    return new HistoryEvidence(institution, id, title, url, HistoryEvidence.Status.FOUND, passage);
                }
                start = end;
            }
        }
        return new HistoryEvidence(institution, id, title, url, HistoryEvidence.Status.NO_PASSAGE, null);
    }
}
