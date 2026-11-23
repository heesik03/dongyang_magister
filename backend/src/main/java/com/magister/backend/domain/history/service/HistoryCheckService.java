package com.magister.backend.domain.history.service;

import com.magister.backend.domain.gemini.service.limit.GeminiRateLimiter;
import com.magister.backend.domain.history.client.EncyKoreaClient;
import com.magister.backend.domain.history.client.HeritageClient;
import com.magister.backend.domain.history.client.HistorySource;
import com.magister.backend.domain.history.converter.HistoryConverter;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryCheck.Assessment;
import com.magister.backend.domain.history.dto.HistoryCheck.Claim;
import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import com.magister.backend.domain.history.dto.HistoryCheck.Kind;
import com.magister.backend.domain.history.dto.HistoryCheck.Status;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HistoryCheckService {
    private static final Pattern SCENE_YEAR = Pattern.compile("([1-9][0-9]{0,3})년");
    private static final Pattern FOUNDATION =
            Pattern.compile("창건|처음.{0,15}(세[우운웠]|지[었은])|최초.{0,15}(건립|건축)");
    private static final Pattern FOUR_DIGIT_YEAR =
            Pattern.compile("(?<![0-9])[1-9][0-9]{3}(?![0-9])");

    private final HistoryClaimModel claimModel;
    private final HeritageClient heritageClient;
    private final EncyKoreaClient encyclopediaClient;
    private final GeminiRateLimiter rateLimiter;

    /** 제출된 원고에서 최대 세 주장을 검토한다. 위치는 UTF-16 시작 포함·끝 제외이며 결과를 저장하지 않는다. */
    public List<Finding> check(long ownerId, String manuscript) {
        return check(ownerId, manuscript, false);
    }

    /** 접수된 비동기 작업은 기존 Gemini 사용자별 호출 간격과 슬롯을 기다린다. */
    public List<Finding> checkJob(long ownerId, String manuscript) {
        return check(ownerId, manuscript, true);
    }

    private List<Finding> check(long ownerId, String manuscript, boolean queued) {
        try (GeminiRateLimiter.Reservation reservation =
                queued ? rateLimiter.awaitReservation(ownerId) : rateLimiter.reserve(ownerId)) {
            Map<Search, List<HistoryCandidate>> searches = new HashMap<>();
            List<Finding> findings = new ArrayList<>();
            List<HistoryClaimModel.Input> inputs = new ArrayList<>();
            List<Claim> claims = claimModel.extract(ownerId, manuscript).claims();

            // 외부 역사 기관 HTTP 조회 구간 동안에는 전역 Gemini 동시 실행 슬롯만 일시 반납해 다른 AI 요청의 병목을 막는다.
            reservation.pauseWhile(
                    () -> {
                        for (Claim claim : claims) {
                            int start = locate(manuscript, claim);
                            Finding finding = lookup(claim, start, searches);
                            if (finding.reason().isEmpty()) {
                                inputs.add(
                                        new HistoryClaimModel.Input(
                                                findings.size(), claim, finding.evidence()));
                            }
                            findings.add(finding);
                        }
                    });

            if (!inputs.isEmpty()) {
                applyAssessments(
                        findings, inputs, claimModel.assess(ownerId, inputs).assessments());
            }
            return List.copyOf(findings);
        }
    }

    private static int locate(String manuscript, Claim claim) {
        // quote 안에 대상명이 반드시 포함되어야 하고, sceneTime이 명시된 경우 원고 본문 어딘가(동일 문장 또는 앞뒤 문맥)에 실제로 존재해야 한다.
        if (!claim.quote().contains(claim.target())
                || (!claim.sceneTime().isEmpty() && !manuscript.contains(claim.sceneTime()))) {
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

    private record Search(HistorySource source, String target) {}

    private Finding lookup(
            Claim claim, int start, Map<Search, List<HistoryCandidate>> searches) {
        List<HistoryEvidence> evidence = new ArrayList<>();
        String reason = "";
        Status status = Status.INSUFFICIENT_EVIDENCE;

        if (claim.kind() == Kind.EXISTENCE && !SCENE_YEAR.matcher(claim.sceneTime()).matches()) {
            reason = "창건 연도와 비교할 장면의 서기 연도를 특정할 수 없습니다.";
        } else {
            boolean sourceFailed = false;
            for (HistorySource source :
                    List.<HistorySource>of(heritageClient, encyclopediaClient)) {
                try {
                    List<HistoryCandidate> exact =
                            searches
                                    .computeIfAbsent(
                                            new Search(source, claim.target()),
                                            search ->
                                                    search.source().candidates(search.target()))
                                    .stream()
                                    .filter(candidate -> candidate.title().equals(claim.target()))
                                    .toList();
                    if (exact.size() > 1) {
                        reason = "동일한 이름의 자료가 여러 건이어서 대상을 특정할 수 없습니다.";
                        break;
                    }
                    if (!exact.isEmpty()) {
                        HistoryEvidence item =
                                source.evidence(exact.getFirst().sourceId(), claim.keywords());
                        if (item.status() == HistoryEvidence.Status.FOUND
                                && item.title().equals(claim.target())) {
                            evidence.add(item);
                        }
                    }
                } catch (CustomException | ResponseStatusException exception) {
                    // 한 기관 조회가 실패해도 다른 기관에서 공식 근거를 확보할 수 있으므로 루프를 계속 진행한다.
                    sourceFailed = true;
                }
            }
            if (evidence.isEmpty() && reason.isEmpty()) {
                if (sourceFailed) {
                    status = Status.LOOKUP_FAILED;
                    reason = "공식 자료 조회를 완료하지 못했습니다.";
                } else {
                    reason = "대상이 일치하고 주장을 대조할 수 있는 공식 대목을 찾지 못했습니다.";
                }
            }
        }
        return HistoryConverter.toFinding(claim, start, status, reason, evidence);
    }

    private static void applyAssessments(
            List<Finding> findings,
            List<HistoryClaimModel.Input> inputs,
            List<Assessment> assessments) {
        if (assessments.size() != inputs.size()) {
            throw HistoryClaimModel.invalidOutput();
        }
        for (HistoryClaimModel.Input input : inputs) {
            List<Assessment> matching =
                    assessments.stream()
                            .filter(item -> item.index() == input.index())
                            .toList();
            if (matching.size() != 1 || matching.getFirst().status() == Status.LOOKUP_FAILED) {
                throw HistoryClaimModel.invalidOutput();
            }
            Finding original = findings.get(input.index());
            Assessment assessment = matching.getFirst();
            Status status = assessment.status();
            String reason = assessment.reason();
            boolean grounded =
                    !assessment.citation().isBlank()
                            && input.evidence().stream()
                                    .anyMatch(
                                            item ->
                                                    item.passage() != null
                                                            && item.passage()
                                                                    .contains(
                                                                            assessment.citation()));
            if (status != Status.INSUFFICIENT_EVIDENCE && !grounded) {
                status = Status.INSUFFICIENT_EVIDENCE;
                reason = "판정의 인용문을 공식 대목에서 확인할 수 없습니다.";
            }
            if (input.claim().kind() == Kind.EXISTENCE
                    && status != Status.INSUFFICIENT_EVIDENCE) {
                Matcher scene = SCENE_YEAR.matcher(original.sceneTime());
                int founding = assessment.foundingYear();
                String citation = assessment.citation();
                if (!scene.matches()
                        || !isFoundingDate(citation, founding)
                        || hasConflictingDate(input.evidence(), founding)) {
                    status = Status.INSUFFICIENT_EVIDENCE;
                    reason = "장면의 연도와 최초 창건 연도를 직접 대조하기 어렵습니다.";
                } else {
                    int year = Integer.parseInt(scene.group(1));
                    status =
                            year < founding
                                    ? Status.POSSIBLE_CONFLICT
                                    : Status.NO_CONFLICT_FOUND;
                    reason =
                            "%s의 창건 연도는 %d년이며 장면은 %d년입니다."
                                    .formatted(original.target(), founding, year);
                }
            }
            findings.set(
                    input.index(), HistoryConverter.toAssessedFinding(original, status, reason));
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
            if (passage == null) {
                continue;
            }
            // 문단 전체가 아니라 창건 표현이 포함된 개별 문장 안에서만 상충하는 창건 연도가 있는지 확인해 후대 중건·지정 연도로 인한 오탐 유보를 방지한다.
            for (String sentence : passage.split("(?<=[.!?。！？])\\s+|\\n+")) {
                if (!FOUNDATION.matcher(sentence).find()) {
                    continue;
                }
                Matcher years = FOUR_DIGIT_YEAR.matcher(sentence);
                while (years.find()) {
                    if (Integer.parseInt(years.group()) != founding) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
