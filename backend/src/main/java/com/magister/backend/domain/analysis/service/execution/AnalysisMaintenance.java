package com.magister.backend.domain.analysis.service.execution;

import com.magister.backend.domain.analysis.service.state.AnalysisLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisMaintenance {
    private final AnalysisDispatcher dispatcher;
    private final AnalysisLeaseService leaseService;

    /** 장애 복구와 다른 서버의 취소를 주기적으로 반영한다. 외부 호출은 이 트랜잭션에 없다. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void maintain() {
        leaseService.maintain(dispatcher.activeJobIds()).forEach(dispatcher::cancel);
    }
}
