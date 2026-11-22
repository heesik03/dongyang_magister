package com.magister.backend.domain.history.service;

import com.magister.backend.domain.gemini.service.limit.GeminiRateLimiter;
import com.magister.backend.domain.history.client.EncyKoreaClient;
import com.magister.backend.domain.history.client.HeritageClient;
import com.magister.backend.domain.history.client.HistorySource;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryCheck.Assessment;
import com.magister.backend.domain.history.dto.HistoryCheck.Claim;
import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import com.magister.backend.domain.history.dto.HistoryCheck.Kind;
import com.magister.backend.domain.history.dto.HistoryCheck.Status;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HistoryCheckService {
    private static final Pattern SCENE_YEAR = Pattern.compile("([1-9][0-9]{0,3})년");
    private static final Pattern FOUNDATION = Pattern.compile("창건|처음.{0,15}(세[우운웠]|지[었은])|최초.{0,15}(건립|건축)");
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("(?<![0-9])[1-9][0-9]{3}(?![0-9])");
    private final HistoryClaimModel model;
    private final HeritageClient heritage;
    private final EncyKoreaClient encyclopedia;
    private final GeminiRateLimiter limiter;

    /** 제출된 원고에서 최대 세 주장을 검토한다. 위치는 UTF-16 시작 포함·끝 제외이며 결과를 저장하지 않는다. */
    public List<Finding> check(long ownerId, String manuscript) {
        try (GeminiRateLimiter.Reservation reservation = limiter.reserve(ownerId)) {
            List<Finding> findings = new ArrayList<>();
            List<HistoryClaimModel.Input> inputs = new ArrayList<>();
            for (Claim claim : model.extract(ownerId, manuscript).claims()) {
                int start = locate(manuscript, claim);
                Finding finding = lookup(claim, start);
                if (finding.reason().isEmpty()) {
                    inputs.add(new HistoryClaimModel.Input(findings.size(), claim, finding.evidence()));
                }
                findings.add(finding);
            }
            if (!inputs.isEmpty()) {
                applyAssessments(findings, inputs, model.assess(ownerId, inputs).assessments());
            }
            return List.copyOf(findings);
        }
    }

    private static int locate(String manuscript, Claim claim) {
        if (!claim.quote().contains(claim.target()) || !claim.quote().contains(claim.sceneTime())) {
            throw HistoryClaimModel.invalidOutput();
        }
        int from = 0;
        int start = -1;
        for (int occurrence = 0; occurrence < claim.occurrence(); occurrence++) {
            start = manuscript.indexOf(claim.quote(), from);
            if (start < 0) {
                throw HistoryClaimModel.invalidOutput();
            }
            from = start + claim.quote().length();
        }
        return start;
    }

    private Finding lookup(Claim claim, int start) {
        List<HistoryEvidence> evidence = new ArrayList<>();
        String reason = "";
        Status status = Status.INSUFFICIENT_EVIDENCE;
        if (claim.kind() == Kind.EXISTENCE && !SCENE_YEAR.matcher(claim.sceneTime()).matches()) {
            reason = "창건 연도와 비교할 장면의 서기 연도를 특정할 수 없습니다.";
        } else {
            for (HistorySource source : List.<HistorySource>of(heritage, encyclopedia)) {
                try {
                    List<HistoryCandidate> exact = source.candidates(claim.target()).stream()
                            .filter(candidate -> candidate.title().equals(claim.target())).toList();
                    if (exact.size() > 1) {
                        reason = "동일한 이름의 자료가 여러 건이어서 대상을 특정할 수 없습니다.";
                        break;
                    }
                    if (!exact.isEmpty()) {
                        HistoryEvidence item = source.evidence(exact.getFirst().sourceId(), claim.keywords());
                        if (item.status() == HistoryEvidence.Status.FOUND && item.title().equals(claim.target())) {
                            evidence.add(item);
                        }
                    }
                } catch (ResponseStatusException exception) {
                    status = Status.LOOKUP_FAILED;
                    reason = "공식 자료 조회를 완료하지 못했습니다.";
                    break;
                }
            }
            if (evidence.isEmpty() && reason.isEmpty()) {
                reason = "대상이 일치하고 주장을 대조할 수 있는 공식 대목을 찾지 못했습니다.";
            }
        }
        return new Finding(claim.quote(), start, start + claim.quote().length(), claim.sceneTime(),
                claim.target(), claim.assertion(), status, reason, List.copyOf(evidence));
    }

    private static void applyAssessments(List<Finding> findings, List<HistoryClaimModel.Input> inputs,
            List<Assessment> assessments) {
        if (assessments.size() != inputs.size()) {
            throw HistoryClaimModel.invalidOutput();
        }
        for (HistoryClaimModel.Input input : inputs) {
            List<Assessment> matching = assessments.stream().filter(item -> item.index() == input.index()).toList();
            if (matching.size() != 1 || matching.getFirst().status() == Status.LOOKUP_FAILED) {
                throw HistoryClaimModel.invalidOutput();
            }
            Finding original = findings.get(input.index());
            Assessment assessment = matching.getFirst();
            Status status = assessment.status();
            String reason = assessment.reason();
            boolean grounded = !assessment.citation().isBlank() && input.evidence().stream()
                    .anyMatch(item -> item.passage() != null && item.passage().contains(assessment.citation()));
            if (status != Status.INSUFFICIENT_EVIDENCE && !grounded) {
                status = Status.INSUFFICIENT_EVIDENCE;
                reason = "판정의 인용문을 공식 대목에서 확인할 수 없습니다.";
            }
            if (input.claim().kind() == Kind.EXISTENCE && status != Status.INSUFFICIENT_EVIDENCE) {
                Matcher scene = SCENE_YEAR.matcher(original.sceneTime());
                int founding = assessment.foundingYear();
                String citation = assessment.citation();
                if (!scene.matches() || !isFoundingDate(citation, founding)
                        || hasConflictingDate(input.evidence(), founding)) {
                    status = Status.INSUFFICIENT_EVIDENCE;
                    reason = "장면의 연도와 최초 창건 연도를 직접 대조하기 어렵습니다.";
                } else {
                    int year = Integer.parseInt(scene.group(1));
                    status = year < founding ? Status.POSSIBLE_CONFLICT : Status.NO_CONFLICT_FOUND;
                    reason = "%s의 창건 연도는 %d년이며 장면은 %d년입니다.".formatted(original.target(), founding, year);
                }
            }
            findings.set(input.index(), new Finding(original.quote(), original.startOffset(), original.endOffset(),
                    original.sceneTime(), original.target(), original.assertion(), status, reason, original.evidence()));
        }
    }

    private static boolean isFoundingDate(String citation, int founding) {
        if (founding < 1 || !FOUNDATION.matcher(citation).find()) {
            return false;
        }
        // 여러 절대 연도가 있으면 창건·중건 연도를 잘못 연결할 수 있다.
        Pattern year = Pattern.compile("(?<![0-9])" + founding + "(?![0-9])");
        return year.matcher(citation).find()
                && !FOUR_DIGIT_YEAR.matcher(year.matcher(citation).replaceAll(" ")).find();
    }

    private static boolean hasConflictingDate(List<HistoryEvidence> evidence, int founding) {
        for (HistoryEvidence source : evidence) {
            String passage = source.passage();
            if (passage == null || !FOUNDATION.matcher(passage).find()) {
                continue;
            }
            Matcher years = FOUR_DIGIT_YEAR.matcher(passage);
            while (years.find()) {
                if (Integer.parseInt(years.group()) != founding) {
                    return true;
                }
            }
        }
        return false;
    }
}
