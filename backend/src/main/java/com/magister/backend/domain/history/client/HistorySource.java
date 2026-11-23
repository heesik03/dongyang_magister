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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface HistorySource {
    Pattern SENTENCE_END =
            Pattern.compile("(?<=[가-힣0-9)\\]』」”'\"]\\.|[!?。！？])\\s+(?=[가-힣0-9(\\[『「“'\"A-Za-z])");

    List<HistoryCandidate> candidates(String query);

    default HistoryEvidence evidence(String sourceId, String keyword) {
        return evidence(sourceId, List.of(keyword));
    }

    HistoryEvidence evidence(String sourceId, List<String> keywords);

    HistoryEvidence document(String sourceId);

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
        List<String> normalizedKeywords = normalizeKeywords(title, keywords);
        List<String> primaryKeywords =
                normalizedKeywords.stream().filter(keyword -> !keyword.equals(title)).toList();
        List<String> activeKeywords =
                primaryKeywords.isEmpty() ? normalizedKeywords : primaryKeywords;
        if (activeKeywords.isEmpty()) {
            return HistoryConverter.toMissingPassageEvidence(institution, id, title, url);
        }

        List<String> bestParagraph = List.of();
        int bestIndex = -1;
        int bestMatch = -1;
        int bestScore = 0;

        for (String line : Arrays.stream(sections).flatMap(String::lines).toList()) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            List<String> sentenceList = splitSentences(line);
            for (int index = 0; index < sentenceList.size(); index++) {
                String sentence = sentenceList.get(index);
                if (sentence.isEmpty()) {
                    continue;
                }
                int score = scoreSentence(sentenceList, index, activeKeywords);
                if (score > bestScore) {
                    int match = firstMatchPosition(sentence, activeKeywords);
                    if (match >= 0) {
                        bestScore = score;
                        bestParagraph = sentenceList;
                        bestIndex = index;
                        bestMatch = match;
                    }
                }
            }
        }

        if (bestScore == 0 && !primaryKeywords.isEmpty() && normalizedKeywords.contains(title)) {
            for (String line : Arrays.stream(sections).flatMap(String::lines).toList()) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                List<String> sentenceList = splitSentences(line);
                for (int index = 0; index < sentenceList.size(); index++) {
                    String sentence = sentenceList.get(index);
                    int match = sentence.indexOf(title);
                    if (match >= 0) {
                        String passage =
                                buildPassage(sentenceList, index, match, List.of(title));
                        return HistoryConverter.toFoundEvidence(
                                institution, id, title, url, passage);
                    }
                }
            }
        }

        if (bestIndex >= 0) {
            // 단일 문장 또는 500자 이내의 관련 인접 문맥과 도입 정의 문장을 함께 묶어 주어·시점 누락을 방지한다.
            String passage =
                    buildPassage(bestParagraph, bestIndex, bestMatch, activeKeywords);
            return HistoryConverter.toFoundEvidence(institution, id, title, url, passage);
        }
        return HistoryConverter.toMissingPassageEvidence(institution, id, title, url);
    }

    private static List<String> normalizeKeywords(String title, List<String> keywords) {
        List<String> result = new ArrayList<>();
        for (String raw : keywords) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.strip();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        if (result.size() > 1 && result.contains(title)) {
            result.remove(title);
            result.add(title);
        }
        return List.copyOf(result);
    }

    private static int scoreSentence(
            List<String> sentenceList, int index, List<String> activeKeywords) {
        String sentence = sentenceList.get(index);
        int directMatches = countMatches(sentence, activeKeywords);
        if (directMatches == 0) {
            return 0;
        }
        int neighborMatches = 0;
        if (index > 0) {
            neighborMatches += countMatches(sentenceList.get(index - 1), activeKeywords);
        }
        if (index + 1 < sentenceList.size()) {
            neighborMatches += countMatches(sentenceList.get(index + 1), activeKeywords);
        }
        int leadBonus = index == 0 ? 3 : (index == 1 ? 1 : 0);
        return directMatches * 100 + neighborMatches * 15 + leadBonus;
    }

    private static int countMatches(String sentence, List<String> activeKeywords) {
        int count = 0;
        for (String keyword : activeKeywords) {
            if (sentence.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private static int firstMatchPosition(String sentence, List<String> activeKeywords) {
        return activeKeywords.stream()
                .mapToInt(sentence::indexOf)
                .filter(position -> position >= 0)
                .min()
                .orElse(-1);
    }

    private static List<String> splitSentences(String line) {
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.KOREAN);
        sentences.setText(line);
        List<String> result = new ArrayList<>();
        int start = sentences.first();
        for (int end = sentences.next(); end != BreakIterator.DONE; end = sentences.next()) {
            String chunk = line.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                splitQuotedChunk(chunk, result);
            }
            start = end;
        }
        return result;
    }

    /** 인용부호(겹낫표·따옴표)가 포함된 문단에서 BreakIterator가 두 문장을 하나로 묶는 현상을 보정한다. */
    private static void splitQuotedChunk(String chunk, List<String> result) {
        Matcher matcher = SENTENCE_END.matcher(chunk);
        int last = 0;
        while (matcher.find()) {
            String part = chunk.substring(last, matcher.start()).strip();
            if (!part.isEmpty()) {
                result.add(part);
            }
            last = matcher.end();
        }
        String tail = chunk.substring(last).strip();
        if (!tail.isEmpty()) {
            result.add(tail);
        }
    }

    private static String buildPassage(
            List<String> sentenceList, int index, int match, List<String> activeKeywords) {
        String sentence = sentenceList.get(index);
        if (sentence.length() > 500) {
            int from = Math.max(0, match - 160);
            return sentence.substring(from, Math.min(sentence.length(), from + 500));
        }

        StringBuilder passage = new StringBuilder(sentence);
        // 매칭된 문장이 문단의 첫 문장(도입 정의)이 아닐 때, 첫 문장 또는 직전 문장에 다른 키워드가 있거나 문맥 연결이 필요하면 500자 한도 안에서 앞에 덧붙인다.
        if (index > 0) {
            String previous = sentenceList.get(index - 1);
            boolean previousHasKeyword = countMatches(previous, activeKeywords) > 0;
            if ((index == 1 || previousHasKeyword)
                    && previous.length() + 1 + passage.length() <= 500) {
                passage.insert(0, previous + " ");
            } else if (index > 1) {
                String lead = sentenceList.getFirst();
                if (countMatches(lead, activeKeywords) > 0
                        && lead.length() + 1 + passage.length() <= 500) {
                    passage.insert(0, lead + " ");
                }
            }
        }
        if (index + 1 < sentenceList.size()) {
            String next = sentenceList.get(index + 1);
            if (countMatches(next, activeKeywords) > 0
                    && passage.length() + 1 + next.length() <= 500) {
                passage.append(" ").append(next);
            }
        }
        return passage.toString();
    }
}
