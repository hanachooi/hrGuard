package dev.leave.service;

import dev.leave.constant.LeaveStatus;
import dev.leave.constant.LeaveType;
import dev.leave.entity.Leave;
import dev.leave.entity.LeaveBalance;
import dev.leave.exception.LeaveError;
import dev.leave.exception.LeaveException;
import dev.leave.repository.LeaveBalanceRepository;
import dev.leave.repository.LeaveRepository;
import dev.workrecord.constant.WorkType;
import dev.workrecord.service.WorkRecordService;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.service.WorkScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final WorkRecordService workRecordService;
    private final WorkScheduleService workScheduleService;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public Leave apply(Long memberId,
                       LocalDateTime startDateTime, LocalDateTime endDateTime,
                       LeaveType leaveType, String reason) {
        if (leaveType.deductsBalance()) {
            LeaveBalance balance = getOrCreateBalance(memberId, startDateTime.getYear());
            double deductionDays = calculateDeductionDays(startDateTime, endDateTime, leaveType);
            if (balance.getRemainingDays() < deductionDays) {
                throw new LeaveException(LeaveError.INSUFFICIENT_BALANCE);
            }
        }
        return leaveRepository.save(Leave.apply(memberId, startDateTime, endDateTime, leaveType, reason));
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 휴가를 승인합니다.
     *
     * <ol>
     *   <li>Leave.approve() 호출 (상태 APPROVED)</li>
     *   <li>구간을 날짜별 WorkRecord(ANNUAL_LEAVE) 슬롯으로 분할 점유 (WorkSchedule 기준)</li>
     * </ol>
     *
     * <p>슬롯 시간은 멤버의 WorkSchedule 기준</p>
     */
    @Transactional
    public void approve(Long leaveId) {
        Leave leave = findById(leaveId);
        leave.approve();

        if (leave.getLeaveType().deductsBalance()) {
            LeaveBalance balance = getOrCreateBalance(
                    leave.getMemberId(), leave.getStartDateTime().getYear());
            balance.deduct(leave.getDeductionDays());
        }

        WorkSchedule schedule = workScheduleService.findByMemberId(leave.getMemberId());
        workRecordService.registerApprovedSlots(
                leave.getMemberId(),
                leave.getStartDateTime(), leave.getEndDateTime(),
                schedule, WorkType.ANNUAL_LEAVE);
    }

    // ── 반려 ─────────────────────────────────────────────────────────────────

    @Transactional
    public void reject(Long leaveId, String reason) {
        findById(leaveId).reject(reason);
    }

    // ── 연차 추가 부여 (HR 전용) ──────────────────────────────────────────────

    @Transactional
    public LeaveBalance grant(Long memberId, int year, double days) {
        LeaveBalance balance = getOrCreateBalance(memberId, year);
        balance.grant(days);
        return balance;
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Leave> findMyLeaves(Long memberId) {
        return leaveRepository.findByMemberIdOrderByStartDateTimeDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<Leave> findPending() {
        return leaveRepository.findByStatusOrderByStartDateTimeDesc(LeaveStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public LeaveBalance getBalance(Long memberId, int year) {
        return getOrCreateBalance(memberId, year);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private Leave findById(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new LeaveException(LeaveError.ANNUAL_LEAVE_NOT_FOUND));
    }

    private LeaveBalance getOrCreateBalance(Long memberId, int year) {
        return balanceRepository.findByMemberIdAndYear(memberId, year)
                .orElseGet(() -> balanceRepository.save(LeaveBalance.init(memberId, year)));
    }

    private double calculateDeductionDays(LocalDateTime startDateTime, LocalDateTime endDateTime,
                                          LeaveType leaveType) {
        long days = ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate()) + 1;
        return days * leaveType.daysPerUnit();
    }

}
