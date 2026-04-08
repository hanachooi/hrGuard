package dev.fieldwork.service;

import dev.fieldwork.constant.FieldWorkStatus;
import dev.fieldwork.entity.FieldWork;
import dev.fieldwork.exception.FieldWorkError;
import dev.fieldwork.exception.FieldWorkException;
import dev.fieldwork.repository.FieldWorkRepository;
import dev.payroll.constant.WorkType;
import dev.payroll.entity.WorkRecord;
import dev.payroll.repository.WorkRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldWorkService {

    private final FieldWorkRepository fieldWorkRepository;
    private final WorkRecordRepository workRecordRepository;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public FieldWork apply(Long memberId, LocalDate workDate,
                           LocalDateTime startTime, LocalDateTime endTime,
                           String location, String purpose) {
        FieldWork fieldWork = FieldWork.apply(memberId, workDate, startTime, endTime, location, purpose);
        return fieldWorkRepository.save(fieldWork);
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 외근을 승인하고 해당 날짜/시간에 대해 WorkRecord(FIELD)를 생성합니다.
     *
     * <p>같은 날 이미 OFFICE WorkRecord가 있어도 별도 세그먼트로 추가됩니다.
     * 배치 Processor가 일별 합산 후 연장·야간 수당을 정확히 계산합니다.</p>
     */
    @Transactional
    public void approve(Long fieldWorkId) {
        FieldWork fieldWork = findById(fieldWorkId);
        fieldWork.approve();

        WorkRecord record = WorkRecord.builder()
                .memberId(fieldWork.getMemberId())
                .bizDate(fieldWork.getWorkDate())
                .startTime(fieldWork.getStartTime())
                .endTime(fieldWork.getEndTime())
                .workType(WorkType.FIELD)
                .build();
        workRecordRepository.save(record);
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
        return fieldWorkRepository.findByStatusOrderByCreatedAtDesc(
                FieldWorkStatus.PENDING);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private FieldWork findById(Long id) {
        return fieldWorkRepository.findById(id)
                .orElseThrow(() -> new FieldWorkException(FieldWorkError.FIELD_WORK_NOT_FOUND));
    }
}
