package com.magister.backend.domain.setting.image;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;
import com.magister.backend.domain.aiquota.AiQuotaService;
import com.magister.backend.domain.aiquota.AiQuotaService.Usage;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.error.SettingErrorCode;
import com.magister.backend.domain.setting.image.CharacterImageData.CreateRequest;
import com.magister.backend.domain.setting.image.CharacterImageData.Gallery;
import com.magister.backend.domain.setting.image.CharacterImageData.Input;
import com.magister.backend.domain.setting.image.CharacterImageData.Job;
import com.magister.backend.domain.setting.image.CharacterImageRepository.Receipt;
import com.magister.backend.domain.setting.image.CharacterImageRepository.StoredImage;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CharacterImageService {
    private static final int CAPACITY = 10;
    private final WorkAccessService workAccessService;
    private final WorkSettingRepository workSettingRepository;
    private final CharacterImageRepository repository;
    private final AiQuotaService quota;

    @Value("${character-image.model:gemini-3.1-flash-image}")
    private String model;

    /** 본인 인물의 시안 메타데이터와 한국 시간 기준 잔여량을 반환한다. */
    @Transactional(readOnly = true)
    public Gallery list(long userId, long workId, long settingId) {
        workAccessService.requireOwned(userId, workId);
        character(workId, settingId);
        Usage usage = quota.usage(userId, Feature.CHARACTER_IMAGE, Instant.now());
        return new Gallery(repository.list(settingId, CAPACITY),
                Math.max(0, usage.limit() - usage.used()), usage.resetAt(), CAPACITY, !model.isBlank());
    }

    /** 작품 잠금 아래에서 중복 확인, 시안 한도 검사, 사용량 차감과 접수를 수행한다. */
    @Transactional
    public Job create(long userId, long workId, long settingId, CreateRequest request) {
        workAccessService.lockOwned(userId, workId);
        WorkSetting setting = character(workId, settingId);
        String prompt = prompt(request.input());
        String requestFingerprint = fingerprint(setting.getVersion(), prompt);
        Optional<Receipt> existing = repository.receipt(settingId, request.requestId());
        if (existing.isPresent()) {
            Receipt receipt = existing.get();
            if (!receipt.fingerprint().equals(requestFingerprint) || receipt.job().status().equals("DELETED")) {
                throw new CustomException(WorkErrorCode.VERSION_CONFLICT);
            }
            return receipt.job();
        }

        requireModel();
        if (repository.count(settingId) >= CAPACITY) {
            throw new CustomException(SettingErrorCode.CHARACTER_IMAGE_CAPACITY);
        }
        quota.charge(userId, Feature.CHARACTER_IMAGE);
        Job job = new Job(UUID.randomUUID().toString(), setting.getVersion(), "QUEUED", false, Instant.now());
        repository.insert(settingId, request.requestId(), requestFingerprint, prompt, model, job);
        return job;
    }

    /** 인물에 속한 완료 이미지만 인증된 소유자에게 반환한다. */
    @Transactional(readOnly = true)
    public StoredImage content(long userId, long workId, long settingId, String id) {
        workAccessService.requireOwned(userId, workId);
        character(workId, settingId);
        return repository.image(settingId, id).orElseThrow(this::missingImage);
    }

    /** 기존 대표 이미지 해제와 새 지정이 하나의 트랜잭션에서 반영된다. */
    @Transactional
    public void selectPrimary(long userId, long workId, long settingId, String id) {
        workAccessService.lockOwned(userId, workId);
        character(workId, settingId);
        repository.clearPrimary(settingId);
        if (id != null && !repository.setPrimary(settingId, id)) {
            throw missingImage();
        }
    }

    /** 완료 또는 실패한 시안을 정리한다. 진행 중 작업은 그대로 둔다. */
    @Transactional
    public void delete(long userId, long workId, long settingId, String id) {
        workAccessService.lockOwned(userId, workId);
        character(workId, settingId);
        if (!repository.delete(settingId, id)) {
            throw missingImage();
        }
    }

    private WorkSetting character(long workId, long settingId) {
        return workSettingRepository.findByIdAndWork_Id(settingId, workId)
                .filter(setting -> setting.getKind() == SettingKind.CHARACTER)
                .orElseThrow(() -> new CustomException(SettingErrorCode.SETTING_NOT_FOUND));
    }

    private void requireModel() {
        if (model.isBlank()) {
            throw new CustomException(SettingErrorCode.CHARACTER_IMAGE_UNAVAILABLE);
        }
    }

    private CustomException missingImage() {
        return new CustomException(SettingErrorCode.CHARACTER_IMAGE_NOT_FOUND);
    }

    private String prompt(Input input) {
        return """
                소설에 등장하는 가상의 인물 한 명을 초상 이미지로 그려 주세요. 글자는 넣지 마세요.
                외형 설명을 우선 적용하고, 연출은 외형과 충돌하지 않는 범위에서 반영하세요.
                외형 설명:
                %s
                연출:
                %s
                """.formatted(input.appearance().strip(), input.direction().strip()).strip();
    }

    private String fingerprint(long settingVersion, String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((settingVersion + "|" + prompt).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
