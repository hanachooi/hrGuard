package dev.leave.service;

import dev.leave.constant.LeaveStatus;
import dev.leave.constant.LeaveType;
import dev.leave.entity.Leave;
import dev.leave.entity.LeaveBalance;
import dev.leave.exception.LeaveError;
import dev.leave.exception.LeaveException;
import dev.leave.repository.LeaveBalanceRepository;
import dev.leave.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final LeaveBalanceRepository balanceRepository;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public Leave apply(Long memberId, LocalDateTime startDateTime, LocalDateTime endDateTime,
                       LeaveType leaveType, String reason) {
        Leave leave = Leave.apply(memberId, startDateTime, endDateTime, leaveType, reason);
        if (leaveType.deductsBalance()) {
            LeaveBalance balance = getOrCreateBalance(memberId, startDateTime.toLocalDate().getYear());
            if (balance.getRemainingDays() < leave.getDeductionDays()) {
                throw new LeaveException(LeaveError.INSUFFICIENT_BALANCE);
            }
        }
        return leaveRepository.save(leave);
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 휴가를 승인합니다.
     *
     * <p>Leave 상태를 APPROVED로 변경하고 연차 잔여일수를 차감합니다.
     * WorkRecord 생성은 배치(WorkRecordComputeProcessor)가 담당합니다.</p>
     */
    @Transactional
    public void approve(Long leaveId) {
        Leave leave = findById(leaveId);
        leave.approve();

        if (leave.getLeaveType().deductsBalance()) {
            LeaveBalance balance = getOrCreateBalance(
                    leave.getMemberId(), leave.getStartDateTime().toLocalDate().getYear());
            balance.deduct(leave.getDeductionDays());
        }
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
}
