package dev.fieldwork.service;

import dev.fieldwork.entity.FieldWork;
import dev.fieldwork.exception.FieldWorkError;
import dev.fieldwork.exception.FieldWorkException;
import dev.fieldwork.repository.FieldWorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldWorkService {

    private final FieldWorkRepository fieldWorkRepository;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public FieldWork apply(Long memberId,
                           LocalDateTime startDateTime, LocalDateTime endDateTime,
                           String location, String purpose) {

        FieldWork fieldWork = FieldWork.apply(memberId, startDateTime, endDateTime, location, purpose);
        return fieldWorkRepository.save(fieldWork);
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 외근을 승인합니다.
     *
     * <p>FieldWork 상태를 APPROVED로 변경합니다.
     * WorkRecord 생성은 배치(WorkRecordComputeProcessor)가 담당합니다.</p>
     */
    @Transactional
    public void approve(Long fieldWorkId) {
        findById(fieldWorkId).approve();
    }

    // ── 반려 ─────────────────────────────────────────────────────────────────

    @Transactional
    public void reject(Long fieldWorkId, String reason) {
        findById(fieldWorkId).reject(reason);
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FieldWork> findMyFieldWorks(Long memberId) {
        return fieldWorkRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<FieldWork> findPending() {
        return fieldWorkRepository.findPending();
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private FieldWork findById(Long id) {
        return fieldWorkRepository.findById(id)
                .orElseThrow(() -> new FieldWorkException(FieldWorkError.FIELD_WORK_NOT_FOUND));
    }
}
