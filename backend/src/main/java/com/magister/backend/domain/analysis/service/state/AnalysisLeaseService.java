package com.magister.backend.domain.analysis.service.state;

import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.execution.AnalysisExecutionProperties;
import com.magister.backend.domain.analysis.service.execution.AnalysisInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisLeaseService {
    private static final List<AnalysisJobStatus> ACTIVE = List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING);
    private static final List<AnalysisJobStatus> TERMINAL = List.of(
            AnalysisJobStatus.SUCCEEDED, AnalysisJobStatus.FAILED, AnalysisJobStatus.CANCELLED);
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisExecutionProperties properties;
    private final AnalysisInstance instance;

    /** 살아 있는 로컬 작업만 연장하고, 다른 서버의 정상 lease는 건드리지 않는다. */
    @Transactional
    public List<String> maintain(List<String> localJobIds) {
        Instant now = Instant.now();
        if (!localJobIds.isEmpty()) {
            analysisJobRepository.renewLeases(instance.id(), localJobIds, ACTIVE,
                    now, now.plus(properties.getLeaseDuration()));
        }
        analysisJobRepository.failExpired(ACTIVE, AnalysisJobStatus.FAILED,
                "작업 실행 연결이 종료되었습니다. 다시 요청해 주세요.", now);
        // 기본값 0은 보존이다. 보존 기간과 멱등 키 유효기간을 정한 운영 설정에서만 삭제한다.
        if (!properties.getRetention().isZero()) {
            List<String> expired = analysisJobRepository.findExpiredResultIds(TERMINAL,
                    now.minus(properties.getRetention()), PageRequest.of(0, 100));
            if (!expired.isEmpty()) analysisJobRepository.deleteAllByIdInBatch(expired);
        }
        return localJobIds.isEmpty() ? List.of() : analysisJobRepository.findTerminalIds(localJobIds, ACTIVE);
    }
}
