package com.magister.backend.domain.analysis.service.state;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.analysis.service.execution.AnalysisInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisJobStateService {
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;
    private final AnalysisInstance instance;

    /** QUEUED만 선점한다. 입력 해석 실패는 롤백한 뒤 워커가 별도 실패 기록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnalysisClaim> claim(String jobId) {
        AnalysisJob job = analysisJobRepository.lockById(jobId).orElseThrow();
        if (job.getWorkerId() != null && !job.getWorkerId().equals(instance.id())) return Optional.empty();
        if (!job.start()) return Optional.empty();
        return Optional.of(new AnalysisClaim(job.getWork().getOwner().getId(),
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
}
