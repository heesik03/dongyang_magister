package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.ValidatedAnalysis;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.global.config.AnalysisExecutionProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 외부 통신 없이 선점·상태 전환·lease 복구만 짧은 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class AnalysisJobStateService {
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;
    private final AnalysisExecutionProperties executionProperties;
    private final ReviewResultService reviewResultService;
    private static final List<AnalysisJobStatus> ACTIVE =
            List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING);
    private static final List<AnalysisJobStatus> TERMINAL =
            List.of(
                    AnalysisJobStatus.SUCCEEDED,
                    AnalysisJobStatus.FAILED,
                    AnalysisJobStatus.CANCELLED);

    /** 트랜잭션 종료 후 워커가 사용할 값이다. 지연 로딩 엔티티는 스레드 경계로 넘기지 않는다. */
    public record Claim(Long ownerId, AnalysisInput input) {}

    /**
     * 자기 서버의 QUEUED 작업만 RUNNING으로 선점하고 불변 입력을 반환한다. 중복 전달·종료된 작업·다른 서버의 작업은 빈 결과로 실행을 생략한다. 입력
     * 역직렬화가 실패하면 선점도 롤백하며, 호출 워커가 별도 실패 트랜잭션을 수행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(String jobId) {
        AnalysisJob job = analysisJobRepository.lockById(jobId).orElseThrow();

        // 한 작업이 여러 실행기에 전달되더라도 배정된 프로세스만 공급자 호출을 시작한다.
        if (job.getWorkerId() != null
                && !job.getWorkerId().equals(executionProperties.getInstanceId())) {
            return Optional.empty();
        }
        if (!job.start()) {
            return Optional.empty();
        }

        return Optional.of(
                new Claim(
                        job.getWork().getOwner().getId(),
                        Objects.requireNonNull(
                                objectMapper.readValue(job.getInput(), AnalysisInput.class))));
    }

    /**
     * 검증된 결과를 저장한다. 외부 호출은 이미 끝난 상태이며 이 메서드 안에서는 수행하지 않는다. 취소·복구 실패가 먼저 확정되었다면 엔티티가 결과를 버려 종료 상태를
     * 덮어쓰지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(String jobId, ValidatedAnalysis result) {
        AnalysisJob job = analysisJobRepository.lockById(jobId).orElseThrow();
        if (job.getStatus() != AnalysisJobStatus.RUNNING) {
            return;
        }
        reviewResultService.save(job, result);
        job.succeed(objectMapper.writeValueAsString(result.result()));
    }

    /**
     * 실행 거절·입력 해석·공급자·검증 실패를 기록한다. 이미 종료된 상태는 보존한다. afterCommit에서는 원 트랜잭션이 다시 커밋되지 않으므로
     * REQUIRES_NEW가 필수다. 호출자는 원고나 공급자 응답 전문이 아닌 사용자에게 공개 가능한 오류만 전달한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String jobId, String message) {
        analysisJobRepository.lockById(jobId).ifPresent(job -> job.fail(message));
    }

    /**
     * 실제 로컬 실행기에 남은 작업의 lease를 갱신한 뒤 만료된 미완료 작업을 실패 처리한다. 다른 서버의 유효한 lease는 유지하며, DB에는 있지만 제출되지 못한
     * 작업은 갱신하지 않는다. 반환한 종료 작업 ID는 호출 실행기가 큐 제거·인터럽트에 사용한다.
     */
    @Transactional
    public List<String> maintain(List<String> localJobIds) {
        Instant now = Instant.now();
        List<String> sortedJobIds = localJobIds.stream().sorted().toList();
        // 취소·실제 종료된 작업과 이미 만료된 lease는 갱신 조건에서 제외해 되살리지 않는다.
        if (!sortedJobIds.isEmpty()) {
            analysisJobRepository.renewLeases(
                    executionProperties.getInstanceId(),
                    sortedJobIds,
                    ACTIVE,
                    now,
                    now.plus(executionProperties.getLeaseDuration()));
        }

        // 재시작 시 전체 작업을 실패시키지 않는다. bulk 갱신은 version도 올려 오래된 저장을 차단한다.
        analysisJobRepository.failExpired(
                ACTIVE, AnalysisJobStatus.FAILED, "작업 실행 연결이 종료되었습니다. 다시 요청해 주세요.", now);

        // 기본값 0은 보존이다. 보존 기간과 멱등 키 유효기간을 정한 운영 설정에서만 삭제한다.
        if (!executionProperties.getRetention().isZero()) {
            List<String> expired =
                    analysisJobRepository.findExpiredResultIds(
                            TERMINAL,
                            now.minus(executionProperties.getRetention()),
                            PageRequest.of(0, 100));
            if (!expired.isEmpty()) {
                analysisJobRepository.deleteExpiredWithoutDecisions(expired);
            }
        }

        return sortedJobIds.isEmpty()
                ? List.of()
                : analysisJobRepository.findTerminalIds(sortedJobIds, ACTIVE);
    }
}
