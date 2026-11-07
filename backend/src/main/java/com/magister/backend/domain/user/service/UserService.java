package com.magister.backend.domain.user.service;

import com.magister.backend.domain.user.converter.UserConverter;
import com.magister.backend.domain.user.dto.ChangePasswordRequest;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UpdateProfileRequest;
import com.magister.backend.domain.user.dto.UserActivityResponse;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.repository.UserActivityRepository;
import com.magister.backend.domain.user.repository.UserActivityRepository.RecentManuscriptRow;
import com.magister.backend.domain.user.repository.UserConstraintTranslator;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.support.EmailNormalizer;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserActivityRepository userActivityRepository;

    /** 가입 가능 여부 안내다. 실제 중복 방지는 가입 저장 시 DB 유일 제약으로 보장한다. */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        // 사전 확인은 화면 안내용이며 가입 시 유일 제약 검증도 반드시 수행한다.
        return !userRepository.existsByEmail(EmailNormalizer.normalize(email));
    }

    /** 해싱은 DB 트랜잭션 밖에서 수행하고 저장 시 제약 위반을 업무 오류로 변환한다. */
    public UserResponse signup(SignupRequest request) {
        // 이메일 대소문자 차이로 계정이 중복되지 않게 저장·조회 기준을 통일한다.
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // 고비용 해시 연산 동안 DB 트랜잭션과 커넥션을 점유하지 않도록 저장 시점에만 트랜잭션을 사용한다.
        // 평문 비밀번호는 저장하지 않으며 응답 DTO에도 포함하지 않는다.
        String passwordHash = passwordEncoder.encode(request.password());
        try {
            User user = new User(email, request.name().strip(), passwordHash);
            User saved = userRepository.saveAndFlush(user);
            return UserConverter.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            if (UserConstraintTranslator.isDuplicateEmail(exception)) {
                // 사전 중복 검사 이후 동시에 가입하는 경우도 DB 유일 제약으로 막는다.
                throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    /** 작가 필명을 수정하고 갱신된 세션용 공개 프로필을 반환한다. 서로 다른 계정의 동일 필명은 허용한다. */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        if (userRepository.updateName(userId, request.name()) != 1) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
        return UserConverter.toResponse(requireUser(userId));
    }

    /** 현재 비밀번호와 확인 입력을 검증한 뒤 새 비밀번호 해시로 교체한다. */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new CustomException(UserErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CustomException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        if (userRepository.updatePassword(userId, user.getSecurityVersion(), newPasswordHash) != 1) {
            throw new CustomException(UserErrorCode.PASSWORD_CHANGE_CONFLICT);
        }
    }

    /** 마이페이지에 표시할 작품 수·저장 회차 수·최신 원고 기준 미검토 후보 수·최근 작업 원고를 조회한다. */
    @Transactional(readOnly = true)
    public UserActivityResponse getActivity(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
        long workCount = userActivityRepository.countOwnedWorks(userId);
        long chapterCount = userActivityRepository.countOwnedChapters(userId);
        long unreviewedCount = userActivityRepository.countUnreviewedLatestCandidates(userId);
        RecentManuscriptRow recent =
                userActivityRepository.findRecentManuscript(userId).orElse(null);
        return UserConverter.toActivityResponse(
                workCount, chapterCount, unreviewedCount, recent);
    }
    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }
}
