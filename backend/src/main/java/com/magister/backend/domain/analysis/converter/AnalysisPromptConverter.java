package com.magister.backend.domain.analysis.converter;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.decision.dto.DecisionReference;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 위치 계산을 모델에 맡기지 않도록 원문에서 계산한 UTF-16 구간을 전송한다. 저장 스냅샷은 바꾸지 않는다. */
@Component
public class AnalysisPromptConverter {
    private static final int MAX_PASSAGE_CHARS = 2000;

    public record Passage(int startOffset, int endOffset, String quote) {}

    public record PromptChapter(String id, String title, long revision, List<Passage> passages) {}

    public record PromptSetting(
            String id,
            String title,
            String kind,
            String status,
            long version,
            List<Passage> passages) {}

    public record PromptData(
            PromptChapter chapter,
            List<PromptSetting> settings,
            List<PromptChapter> previous,
            boolean consistency,
            boolean motivation,
            List<DecisionReference> decisionReferences) {}

    /** 선택된 자료만 투영한다. 과거 workspace의 범위 없는 판단을 새 분석에 전송하지 않는다. */
    public PromptData toPayload(AnalysisInput input) {
        return new PromptData(
                toChapter(input.chapter()),
                input.settings().stream().map(this::toSetting).toList(),
                input.previous().stream().map(this::toChapter).toList(),
                input.consistency(),
                input.motivation(),
                input.decisionReferences());
    }

    private PromptChapter toChapter(ChapterData chapter) {
        return new PromptChapter(
                chapter.id(), chapter.title(), chapter.revision(), passages(chapter.content()));
    }

    private PromptSetting toSetting(SettingReference setting) {
        return new PromptSetting(
                setting.id(),
                setting.title(),
                setting.kind(),
                setting.status(),
                setting.version(),
                passages(setting.content()));
    }

    /** 문장·줄 경계와 길이 상한으로 나누며 공백만 제외한다. 반복 문장도 원래 위치를 각각 유지한다. */
    public List<Passage> passages(String content) {
        List<Passage> result = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            while (start < content.length() && Character.isWhitespace(content.charAt(start))) {
                start++;
            }
            if (start == content.length()) {
                break;
            }
            int limit = Math.min(content.length(), start + MAX_PASSAGE_CHARS);
            if (limit < content.length()
                    && Character.isHighSurrogate(content.charAt(limit - 1))
                    && Character.isLowSurrogate(content.charAt(limit))) {
                limit--;
            }
            int end = limit;
            for (int index = start; index < limit; index++) {
                char character = content.charAt(index);
                if (character == '\n' || character == '\r') {
                    end = index;
                    break;
                }
                if (".!?。！？".indexOf(character) >= 0
                        && (index + 1 == content.length()
                                || Character.isWhitespace(content.charAt(index + 1)))) {
                    end = index + 1;
                    break;
                }
            }
            while (end > start && Character.isWhitespace(content.charAt(end - 1))) {
                end--;
            }
            result.add(new Passage(start, end, content.substring(start, end)));
            start = end;
        }
        return List.copyOf(result);
    }
}
