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
import com.magister.backend.domain.history.dto.HistoryCheck.SourceLookup;
import com.magister.backend.domain.history.dto.HistoryCheck.LookupStatus;
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
            Pattern.compile("창건|건립|처음.{0,25}(세[우운웠]|지[었은])|최초.{0,25}(건설|건축)|세웠다|지어졌다");
    private static final Pattern REBUILD = Pattern.compile("중건|재건|다시.{0,12}(지[어었은]|세[운웠])");
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
            Map<Search, HistoryEvidence> documents = new HashMap<>();
            List<Finding> findings = new ArrayList<>();
            List<HistoryClaimModel.Input> inputs = new ArrayList<>();
            List<Claim> claims = claimModel.extract(ownerId, manuscript).claims();

            // 외부 역사 기관 HTTP 조회 구간 동안에는 전역 Gemini 동시 실행 슬롯만 일시 반납해 다른 AI 요청의 병목을 막는다.
            reservation.pauseWhile(
                    () -> {
                        for (Claim extracted : claims) {
                            Claim claim = normalizeEvent(extracted);
                            int start = locate(manuscript, claim);
                            Finding finding = lookup(claim, start, searches, documents, manuscript);
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

    // 방문 여부로 잘못 분류된 단정형 건립 서술에 존재 시점의 부등식을 적용하지 않는다.
    private static final Pattern CONSTRUCTION_STATEMENT = Pattern.compile(
            "(창건|건립|건설|중건|재건)(?:되었다|됐다|하였다|했다|되었으며|되었고)[.!。]?$");

    private static Claim normalizeEvent(Claim claim) {
        if (claim.kind() != Kind.EXISTENCE || mentionsDifferentBuilding(claim.quote(), claim.target())) {
            return claim;
        }
        Matcher action = CONSTRUCTION_STATEMENT.matcher(claim.quote().strip());
        if (!action.find()) {
            return claim;
        }
        Kind kind = REBUILD.matcher(action.group(1)).find() ? Kind.RECONSTRUCTION : Kind.FOUNDATION;
        String event = kind == Kind.FOUNDATION ? "최초 건립" : "중건";
        return new Claim(claim.quote(), claim.occurrence(), claim.target(), claim.sceneTime(),
                claim.target() + "의 " + event + " 시점", kind, claim.keywords());
    }

    private static int locate(String manuscript, Claim claim) {
        if (!claim.quote().contains(claim.target())) {
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

    private Finding lookup(Claim claim, int start, Map<Search, List<HistoryCandidate>> searches,
            Map<Search, HistoryEvidence> documents, String manuscript) {
        List<HistoryEvidence> evidence = new ArrayList<>();
        List<SourceLookup> lookups = new ArrayList<>();
        String reason = "";
        Status status = Status.INSUFFICIENT_EVIDENCE;
        if (!hasLocalTime(manuscript, start, claim)) {
            reason = "인용문과 바로 앞 문맥에서 원고의 시점을 확인할 수 없습니다.";
        } else if (claim.kind() != Kind.OTHER && !SCENE_YEAR.matcher(claim.sceneTime()).matches()) {
            reason = "사건과 비교할 원고의 서기 연도를 특정할 수 없습니다.";
        } else {
            for (HistorySource source : List.<HistorySource>of(heritageClient, encyclopediaClient)) {
                String institution = source == heritageClient ? "국가유산청" : "한국학중앙연구원";
                LookupStatus outcome;
                try {
                    List<HistoryCandidate> exact = searches.computeIfAbsent(
                            new Search(source, claim.target()), key -> source.candidates(key.target()))
                            .stream().filter(candidate -> candidate.title().equals(claim.target())).toList();
                    if (exact.isEmpty()) {
                        outcome = LookupStatus.NO_MATCH;
                    } else if (exact.size() > 1) {
                        outcome = LookupStatus.AMBIGUOUS;
                    } else {
                        String id = exact.getFirst().sourceId();
                        HistoryEvidence item = documents.computeIfAbsent(new Search(source, id),
                                key -> source.document(key.target()));
                        if (!item.title().equals(claim.target())) {
                            outcome = LookupStatus.NO_MATCH;
                        } else if (item.status() == HistoryEvidence.Status.FOUND) {
                            outcome = LookupStatus.REVIEWED;
                            evidence.add(item);
                        } else {
                            outcome = LookupStatus.EMPTY;
                        }
                    }
                } catch (CustomException | ResponseStatusException unavailable) {
                    outcome = LookupStatus.FAILED;
                }
                lookups.add(new SourceLookup(institution, outcome));
            }
            if (lookups.stream().anyMatch(item -> item.status() == LookupStatus.AMBIGUOUS)) {
                reason = "동일한 이름의 자료가 여러 건이어서 대상을 특정할 수 없습니다.";
            } else if (evidence.isEmpty()) {
                if (lookups.stream().anyMatch(item -> item.status() == LookupStatus.FAILED)) {
                    status = Status.LOOKUP_FAILED;
                    reason = "공식 자료 조회를 완료하지 못했습니다.";
                } else {
                    reason = "대상이 일치하고 검토할 내용이 있는 공식 자료를 찾지 못했습니다.";
                }
            }
        }
        return HistoryConverter.toFinding(claim, start, status, reason, evidence, lookups);
    }

    private static boolean hasLocalTime(String manuscript, int start, Claim claim) {
        if (claim.sceneTime().isEmpty() || claim.quote().contains(claim.sceneTime())) {
            return true;
        }
        String prefix = manuscript.substring(Math.max(0, start - 180), start);
        int time = prefix.lastIndexOf(claim.sceneTime());
        if (time < 0) {
            return false;
        }
        String after = prefix.substring(time + claim.sceneTime().length());
        return !SCENE_YEAR.matcher(after).find() && !after.matches("(?s).*\\R[\\t ]*\\R.*")
                && !after.stripTrailing().matches("(?s).*[.!?。]\\s+\\S.*");

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
            Integer officialYear = null;
            if (input.claim().kind() != Kind.OTHER
                    && status != Status.INSUFFICIENT_EVIDENCE) {
                Matcher scene = SCENE_YEAR.matcher(original.sceneTime());
                int eventYear = assessment.eventYear();
                String citation = assessment.citation();
                if (!scene.matches()
                        || !isEventDate(citation, eventYear, original.kind(), original.target())
                        || (original.kind() != Kind.RECONSTRUCTION && hasConflictingDate(input.evidence(), eventYear, original.target()))) {
                    status = Status.INSUFFICIENT_EVIDENCE;
                    reason = "원고의 시점과 공식 자료의 사건 연도를 직접 대조하기 어렵습니다.";
                } else {
                    int year = Integer.parseInt(scene.group(1));
                    officialYear = eventYear;
                    if (original.kind() == Kind.EXISTENCE) {
                        status = year < eventYear ? Status.POSSIBLE_CONFLICT : Status.NO_CONFLICT_FOUND;
                        reason = "%s의 창건 연도는 %d년입니다. %d년 장면은 창건 시점%s."
                                .formatted(original.target(), eventYear, year, year < eventYear ? "보다 앞섭니다" : "과 충돌하지 않습니다");
                    } else if (original.kind() == Kind.RECONSTRUCTION && year != eventYear
                            && assessment.status() != Status.POSSIBLE_CONFLICT) {
                        status = Status.INSUFFICIENT_EVIDENCE;
                        officialYear = null;
                        reason = "자료에서 확인한 중건과 원고가 가리키는 중건이 같은 사건인지 확인하기 어렵습니다.";
                    } else {
                        status = year == eventYear ? Status.NO_CONFLICT_FOUND : Status.POSSIBLE_CONFLICT;
                        reason = "%s의 %s 연도는 자료에서 %d년, 원고에서 %d년입니다."
                                .formatted(original.target(), original.kind() == Kind.FOUNDATION ? "최초 건립" : "해당 중건", eventYear, year);
                    }
                }
            }
            findings.set(
                    input.index(), HistoryConverter.toAssessedFinding(original, status, reason,
                            grounded && status != Status.INSUFFICIENT_EVIDENCE ? assessment.citation() : null, officialYear));
        }
    }

    // 한 문장에 창건과 중건을 함께 기술한 경우 사건별 절에서 연도를 검사한다.
    private static final Pattern CLAUSE = Pattern.compile(
            "[,;。.!?\\n]+|(?<=하고)\\s+|(?<=했으며)\\s+|(?<=하였으며)\\s+|(?<=되었고)\\s+");
    private static final Pattern BUILDING = Pattern.compile("([가-힣]{2,}(?:궁|전|루|각|문))(?:은|는|을|를|이|가|의)");

    private static boolean mentionsDifferentBuilding(String clause, String target) {
        Matcher buildings = BUILDING.matcher(clause);
        while (buildings.find()) {
            if (!target.contains(buildings.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEventDate(String citation, int year, Kind kind, String target) {
        if (year < 1) {
            return false;
        }
        Pattern event = kind == Kind.RECONSTRUCTION ? REBUILD : FOUNDATION;
        Pattern claimedYear = Pattern.compile("(?<![0-9])" + year + "(?![0-9])");
        for (String clause : CLAUSE.split(citation)) {
            if (!event.matcher(clause).find() || mentionsDifferentBuilding(clause, target)
                    || (kind != Kind.RECONSTRUCTION && REBUILD.matcher(clause).find())) {
                continue;
            }
            if (claimedYear.matcher(clause).find()
                    && !FOUR_DIGIT_YEAR.matcher(claimedYear.matcher(clause).replaceAll(" ")).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConflictingDate(List<HistoryEvidence> evidence, int founding, String target) {
        for (HistoryEvidence source : evidence) {
            String text = source.passage();
            for (String clause : CLAUSE.split(text)) {
                if (!FOUNDATION.matcher(clause).find() || REBUILD.matcher(clause).find()
                        || mentionsDifferentBuilding(clause, target)) {
                    continue;
                }
                Matcher years = FOUR_DIGIT_YEAR.matcher(clause);
                while (years.find()) {
                    int year = Integer.parseInt(years.group());
                    if (year != founding && isEventDate(clause, year, Kind.FOUNDATION, target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
