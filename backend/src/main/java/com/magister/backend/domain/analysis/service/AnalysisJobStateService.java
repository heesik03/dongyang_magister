package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.global.config.AnalysisExecutionProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisJobStateService {
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;
    private final AnalysisExecutionProperties properties;
    private static final List<AnalysisJobStatus> ACTIVE = List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING);
    private static final List<AnalysisJobStatus> TERMINAL = List.of(
            AnalysisJobStatus.SUCCEEDED, AnalysisJobStatus.FAILED, AnalysisJobStatus.CANCELLED);

    public record Claim(Long ownerId, AnalysisInput input) {}

    /** QUEUED만 선점한다. 입력 해석 실패는 롤백한 뒤 워커가 별도 실패 기록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(String jobId) {
        AnalysisJob job = analysisJobRepository.lockById(jobId).orElseThrow();

        if (job.getWorkerId() != null && !job.getWorkerId().equals(properties.getInstanceId())) {
            return Optional.empty();
        }
        if (!job.start()) {
            return Optional.empty();
        }

        return Optional.of(new Claim(job.getWork().getOwner().getId(),
                objectMapper.readValue(job.getInput(), AnalysisInput.class)));
    }

    /** 외부 호출이 끝난 후 별도 트랜잭션으로 결과를 반영한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(String jobId, AnalysisData result) {
        analysisJobRepository.lockById(jobId).orElseThrow()
                .succeed(objectMapper.writeValueAsString(result));
    }

    /** afterCommit에서도 독립 커밋을 보장하고 이미 종료된 상태는 보존한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String jobId, String message) {
        analysisJobRepository.lockById(jobId).ifPresent(job -> job.fail(message));
    }

    /** 살아 있는 로컬 작업만 연장하고, 다른 서버의 정상 lease는 건드리지 않는다. */
    @Transactional
    public List<String> maintain(List<String> localJobIds) {
        Instant now = Instant.now();
        if (!localJobIds.isEmpty()) {
            analysisJobRepository.renewLeases(properties.getInstanceId(), localJobIds, ACTIVE,
                    now, now.plus(properties.getLeaseDuration()));
        }

        analysisJobRepository.failExpired(ACTIVE, AnalysisJobStatus.FAILED,
                "작업 실행 연결이 종료되었습니다. 다시 요청해 주세요.", now);

        // 기본값 0은 보존이다. 보존 기간과 멱등 키 유효기간을 정한 운영 설정에서만 삭제한다.
        if (!properties.getRetention().isZero()) {
            List<String> expired = analysisJobRepository.findExpiredResultIds(TERMINAL,
                    now.minus(properties.getRetention()), PageRequest.of(0, 100));
            if (!expired.isEmpty()) {
                analysisJobRepository.deleteAllByIdInBatch(expired);
            }
        }

        return localJobIds.isEmpty() ? List.of() : analysisJobRepository.findTerminalIds(localJobIds, ACTIVE);
    }
}
