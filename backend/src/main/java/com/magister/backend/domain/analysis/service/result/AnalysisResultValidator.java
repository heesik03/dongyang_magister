package com.magister.backend.domain.analysis.service.result;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisInput.SettingReference;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AnalysisResultValidator {
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public record Candidate(
            @NotNull @Pattern(regexp = "설정 충돌 후보|설명 보완") String type,
            @NotBlank @Size(max = 10000) String quote,
            @NotBlank @Size(max = 64) String settingId,
            @NotBlank @Size(max = 4000) String reason,
            @NotBlank @Size(max = 4000) String suggestion) {}

    public record Output(@NotNull @Size(max = 100) List<@NotNull @Valid Candidate> reviews) {}
    private record EvidenceKey(String settingId, String quote, String evidence) {}

    /** 공급자 형식뿐 아니라 실제 원문·설정 ID와 옵션까지 검사한 뒤 결과로 변환한다. */
    public AnalysisData validate(String raw, AnalysisInput input) {
        Output output = parse(raw);
        Map<String, SettingReference> settingsById = input.settings().stream()
                .collect(Collectors.toMap(SettingReference::id, Function.identity()));
        Map<EvidenceKey, ReviewData> decisionsByEvidence = new HashMap<>();
        for (ReviewData decision : input.decisions()) {
            decisionsByEvidence.putIfAbsent(new EvidenceKey(decision.settingId(), decision.quote(),
                    decision.evidence()), decision);
        }
        List<ReviewData> reviews = new ArrayList<>();
        for (Candidate candidate : output.reviews()) {
            SettingReference setting = settingsById.get(candidate.settingId());
            if (setting == null || !input.chapter().content().contains(candidate.quote())
                    || (!input.consistency() && candidate.type().equals("설정 충돌 후보"))
                    || (!input.motivation() && candidate.type().equals("설명 보완"))) invalid();
            // 같은 회차라도 근거가 달라지면 과거 판단을 자동 적용하지 않는다.
            Optional<ReviewData> previous = Optional.ofNullable(decisionsByEvidence.get(
                    new EvidenceKey(setting.id(), candidate.quote(), setting.content())));
            reviews.add(new ReviewData(UUID.randomUUID().toString(), candidate.type(),
                    candidate.quote(), setting.id(), setting.content(), candidate.reason(),
                    candidate.suggestion(), previous.map(ReviewData::decision).orElse("미검토"),
                    previous.map(ReviewData::note).orElse("")));
        }
        return new AnalysisData(input.chapter().id(), input.chapter().revision(),
                input.chapter().content(), input.settings().stream().map(SettingReference::id).toList(),
                input.previous().stream().map(ChapterData::id).toList(), reviews,
                Instant.now().toString(), objectMapper.writeValueAsString(input.settings()));
    }

    private Output parse(String raw) {
        try {
            if (raw == null || raw.length() > MAX_RESPONSE_CHARS) throw new IllegalArgumentException();
            Output output = objectMapper.readValue(raw, Output.class);
            if (!validator.validate(output).isEmpty()) throw new IllegalArgumentException();
            return output;
        } catch (RuntimeException error) {
            throw new CustomException(GeminiErrorCode.INVALID_RESPONSE);
        }
    }

    private void invalid() {
        throw new CustomException(GeminiErrorCode.INVALID_RESPONSE);
    }
}
