package com.magister.backend.domain.timeline.service;

import com.magister.backend.domain.timeline.dto.ExtractionData.Candidate;
import com.magister.backend.domain.timeline.dto.ExtractionData.Input;
import com.magister.backend.domain.timeline.entity.EventExtraction;
import com.magister.backend.domain.timeline.repository.EventExtractionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExtractionStateService {
    private final EventExtractionRepository eventExtractionRepository;
    private final ObjectMapper objectMapper;

    public record Claim(Long ownerId, Input input) {}

    /** 선점 트랜잭션이 끝난 후에만 공급자 호출을 시작한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(String id) {
        Optional<EventExtraction> found = eventExtractionRepository.lockById(id);
        if (found.isEmpty() || !found.get().start()) {
            return Optional.empty();
        }
        EventExtraction extraction = found.get();
        return Optional.of(
                new Claim(
                        extraction.getWork().getOwner().getId(),
                        objectMapper.readValue(extraction.getInput(), Input.class)));
    }

    /** 취소가 먼저 확정되면 늦은 결과를 버린다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(String id, List<Candidate> result) {
        eventExtractionRepository
                .lockById(id)
                .ifPresent(
                        extraction -> extraction.succeed(objectMapper.writeValueAsString(result)));
    }

    /** 공급자 예외 전문과 원고는 영속 오류 메시지에 포함하지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String id) {
        eventExtractionRepository
                .lockById(id)
                .ifPresent(
                        extraction ->
                                extraction.fail("사건 추출에 실패했습니다. 원고와 기존 사건은 유지됩니다. 다시 요청해 주세요."));
    }

    /** 서버 중단·실행 유실은 유한 lease로 복구하며 후보는 보존한다. */
    @Transactional
    public void expire() {
        eventExtractionRepository.expire(Instant.now());
    }
}
