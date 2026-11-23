package com.magister.backend.domain.history.client;

import com.magister.backend.domain.history.converter.HistoryConverter;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;

import java.net.URI;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public interface HistorySource {
    List<HistoryCandidate> candidates(String query);

    default HistoryEvidence evidence(String sourceId, String keyword) {
        return evidence(sourceId, List.of(keyword));
    }

    HistoryEvidence evidence(String sourceId, List<String> keywords);

    static List<HistoryCandidate> ranked(List<HistoryCandidate> candidates, String query) {
        return candidates.stream()
                .sorted(Comparator.comparing(candidate -> !candidate.title().equals(query)))
                .limit(10)
                .toList();
    }

    static HistoryEvidence excerpt(
            String institution,
            String id,
            String title,
            URI url,
            List<String> keywords,
            String... sections) {
        for (String line : Arrays.stream(sections).flatMap(String::lines).toList()) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            List<String> sentenceList = splitSentences(line);
            for (int index = 0; index < sentenceList.size(); index++) {
                String sentence = sentenceList.get(index);
                int match =
                        keywords.stream()
                                .mapToInt(sentence::indexOf)
                                .filter(position -> position >= 0)
                                .min()
                                .orElse(-1);
                if (match >= 0 && !sentence.isEmpty()) {
                    // 단일 문장이면 기존처럼 해당 문장만 반환하고, 여러 문장이 있는 문단이면 앞뒤 문맥이 500자 이내일 때 함께 묶어 주어·연도 누락을 줄인다.
                    String passage = buildPassage(sentenceList, index, match);
                    return HistoryConverter.toFoundEvidence(institution, id, title, url, passage);
                }
            }
        }
        return HistoryConverter.toMissingPassageEvidence(institution, id, title, url);
    }

    private static List<String> splitSentences(String line) {
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.KOREAN);
        sentences.setText(line);
        List<String> result = new ArrayList<>();
        int start = sentences.first();
        for (int end = sentences.next(); end != BreakIterator.DONE; end = sentences.next()) {
            String sentence = line.substring(start, end).strip();
            if (!sentence.isEmpty()) {
                result.add(sentence);
            }
            start = end;
        }
        return result;
    }

    private static String buildPassage(List<String> sentenceList, int index, int match) {
        String sentence = sentenceList.get(index);
        if (sentence.length() > 500) {
            int from = Math.max(0, match - 160);
            return sentence.substring(from, Math.min(sentence.length(), from + 500));
        }
        return sentence;
    }
}
