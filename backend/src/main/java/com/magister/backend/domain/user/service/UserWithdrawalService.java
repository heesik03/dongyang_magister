package com.magister.backend.domain.user.service;

import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.timeline.service.ExtractionDispatcher;
import com.magister.backend.domain.user.dto.WithdrawUserRequest;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.repository.UserDeletionRepository;
import com.magister.backend.domain.user.repository.UserDeletionRepository.LockedUser;
import com.magister.backend.domain.work.repository.WorkDeletionRepository;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {
    private final UserDeletionRepository userDeletionRepository;
    private final WorkDeletionRepository workDeletionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalysisDispatcher analysisDispatcher;
    private final ExtractionDispatcher extractionDispatcher;

    /**
     * 영구 삭제 동의와 현재 비밀번호를 확인한 뒤 소유 작품·하위 기록·진행 중 AI 작업을 모두 정리하고 계정을 삭제한다. 커밋 후에는 로컬 실행기의 분석·추출 작업도
     * 중단한다.
     */
    @Transactional
    public void withdraw(Long userId, WithdrawUserRequest request) {
        if (!request.agreed()) {
            throw new CustomException(UserErrorCode.WITHDRAWAL_AGREEMENT_REQUIRED);
        }
        LockedUser user =
                userDeletionRepository
                        .lockUser(userId)
                        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new CustomException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        List<Long> workIds = userDeletionRepository.lockOwnedWorkIds(userId);
        List<String> activeAnalysisIds = new ArrayList<>();
        List<String> activeExtractionIds = new ArrayList<>();

        for (Long workId : workIds) {
            workDeletionRepository.lockJobs(workId).stream()
                    .filter(
                            job ->
                                    job.status().equals("QUEUED")
                                            || job.status().equals("RUNNING"))
                    .map(WorkDeletionRepository.LockedJob::id)
                    .forEach(activeAnalysisIds::add);
            workDeletionRepository.lockExtractions(workId).stream()
                    .filter(
                            extraction ->
                                    extraction.status().equals("QUEUED")
                                            || extraction.status().equals("RUNNING"))
                    .map(WorkDeletionRepository.LockedJob::id)
                    .forEach(activeExtractionIds::add);
            workDeletionRepository.delete(workId);
        }

        userDeletionRepository.deleteUser(userId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            activeAnalysisIds.forEach(analysisDispatcher::cancel);
                            activeExtractionIds.forEach(extractionDispatcher::cancel);
                        }
                    });
        }
    }
}
