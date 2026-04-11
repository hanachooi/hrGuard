package dev.fieldwork.service;

import dev.fieldwork.constant.FieldWorkStatus;
import dev.fieldwork.entity.FieldWork;
import dev.fieldwork.exception.FieldWorkError;
import dev.fieldwork.exception.FieldWorkException;
import dev.fieldwork.repository.FieldWorkRepository;
import dev.workrecord.constant.WorkType;
import dev.workrecord.service.WorkRecordService;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.service.WorkScheduleService;
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
    private final WorkRecordService workRecordService;
    private final WorkScheduleService workScheduleService;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public FieldWork apply(Long memberId,
                           LocalDateTime startDateTime, LocalDateTime endDateTime,
                           String location, String purpose) {
        return fieldWorkRepository.save(
                FieldWork.apply(memberId, startDateTime, endDateTime, location, purpose));
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 외근을 승인하고 구간을 날짜별 WorkRecord 슬롯으로 분할하여 저장합니다.
     *
     * <p>슬롯 시간은 멤버의 WorkSchedule 기준이며, 없으면 기본값(09:00~18:00)을 사용합니다.</p>
     */
    @Transactional
    public void approve(Long fieldWorkId) {
        FieldWork fieldWork = findById(fieldWorkId);
        fieldWork.approve();

        WorkSchedule schedule = workScheduleService.findByMemberId(fieldWork.getMemberId());
        workRecordService.registerApprovedSlots(
                fieldWork.getMemberId(),
                fieldWork.getStartDateTime(), fieldWork.getEndDateTime(),
                schedule, WorkType.FIELD);
    }

    // ── 반려 ─────────────────────────────────────────────────────────────────

    @Transactional
    public void reject(Long fieldWorkId, String reason) {
        findById(fieldWorkId).reject(reason);
    }

    // ── 취소 (승인 후 취소) ───────────────────────────────────────────────────

    @Transactional
    public void cancel(Long fieldWorkId) {
        FieldWork fieldWork = findById(fieldWorkId);
        LocalDate date = fieldWork.getStartDateTime().toLocalDate();
        LocalDate endDate = fieldWork.getEndDateTime().toLocalDate();
        while (!date.isAfter(endDate)) {
            workRecordService.removeApprovedSlots(fieldWork.getMemberId(), date, WorkType.FIELD);
            date = date.plusDays(1);
        }
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FieldWork> findMyFieldWorks(Long memberId) {
        return fieldWorkRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<FieldWork> findPending() {
        return fieldWorkRepository.findByStatusOrderByCreatedAtDesc(FieldWorkStatus.PENDING);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private FieldWork findById(Long id) {
        return fieldWorkRepository.findById(id)
                .orElseThrow(() -> new FieldWorkException(FieldWorkError.FIELD_WORK_NOT_FOUND));
    }

}
