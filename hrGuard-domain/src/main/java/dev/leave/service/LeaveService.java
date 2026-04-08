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
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);
    private static final double DEFAULT_DAILY_HOURS = 8.0;

    // 반차 고정 시간 (09:00~13:00, 13:00~17:00)
    private static final LocalTime HALF_AM_END = LocalTime.of(13, 0);
    private static final LocalTime HALF_PM_START = LocalTime.of(13, 0);
    private static final LocalTime HALF_PM_END = LocalTime.of(17, 0);

    private final LeaveRepository leaveRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkScheduleRepository workScheduleRepository;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public Leave apply(Long memberId, LocalDate startDate, LocalDate endDate,
                       LeaveType leaveType, String reason) {
        // 잔여 일수 선검증 (SICK/PUBLIC은 차감 없으므로 패스)
        if (leaveType.deductsBalance()) {
            LeaveBalance balance = getOrCreateBalance(memberId, startDate.getYear());
            double deductionDays = calculateDeductionDays(startDate, endDate, leaveType);
            if (balance.getRemainingDays() < deductionDays) {
                throw new LeaveException(LeaveError.INSUFFICIENT_BALANCE);
            }
        }
        return leaveRepository.save(
                Leave.apply(memberId, startDate, endDate, leaveType, reason));
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 휴가를 승인합니다.
     *
     * <ol>
     *   <li>Leave.approve() 호출 (상태 APPROVED)</li>
     *   <li>연차 잔여일수 차감 (ANNUAL / HALF_AM / HALF_PM에 한해)</li>
     *   <li>각 날짜에 WorkRecord(ANNUAL_LEAVE) 생성 → 배치가 정상 급여 계산</li>
     * </ol>
     *
     * <h3>WorkRecord 시간</h3>
     * <ul>
     *   <li>ANNUAL / SICK / PUBLIC : 09:00 ~ (09:00 + dailyWorkHours)</li>
     *   <li>HALF_AM : 09:00 ~ 13:00</li>
     *   <li>HALF_PM : 13:00 ~ 17:00</li>
     * </ul>
     * TimeSegmentSplitter는 ANNUAL_LEAVE 유형에 대해 휴게시간을 차감하지 않으므로
     * 정확한 시간이 그대로 급여에 반영됩니다.
     */
    @Transactional
    public void approve(Long leaveId) {
        Leave leave = findById(leaveId);
        leave.approve();

        // ① 연차 잔여일수 차감
        if (leave.getLeaveType().deductsBalance()) {
            LeaveBalance balance = getOrCreateBalance(
                    leave.getMemberId(), leave.getStartDate().getYear());
            balance.deduct(leave.getDeductionDays());
        }

        // ② 날짜별 WorkRecord 생성
        WorkSchedule schedule = workScheduleRepository.findByMemberId(leave.getMemberId())
                .orElse(null);

        List<WorkRecord> records = new ArrayList<>();
        LocalDate date = leave.getStartDate();
        while (!date.isAfter(leave.getEndDate())) {
            records.add(buildWorkRecord(leave.getMemberId(), date, leave.getLeaveType(), schedule));
            date = date.plusDays(1);
        }
        workRecordRepository.saveAll(records);
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
        return leaveRepository.findByMemberIdOrderByStartDateDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<Leave> findPending() {
        return leaveRepository.findByStatusOrderByStartDateDesc(LeaveStatus.PENDING);
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

    private double calculateDeductionDays(LocalDate startDate, LocalDate endDate, LeaveType leaveType) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return days * leaveType.daysPerUnit();
    }

    private WorkRecord buildWorkRecord(Long memberId, LocalDate date, LeaveType leaveType, WorkSchedule schedule) {
        LocalTime workStart = (schedule != null) ? schedule.getStartTime() : DEFAULT_START_TIME;
        LocalTime workEnd = (schedule != null) ? schedule.getEndTime() : DEFAULT_START_TIME.plusMinutes((long) (DEFAULT_DAILY_HOURS * 60));

        LocalDateTime startTime;
        LocalDateTime endTime;

        switch (leaveType) {
            case HALF_AM -> {
                startTime = LocalDateTime.of(date, workStart);
                endTime = LocalDateTime.of(date, HALF_AM_END);
            }
            case HALF_PM -> {
                startTime = LocalDateTime.of(date, HALF_PM_START);
                endTime = LocalDateTime.of(date, HALF_PM_END);
            }
            default -> {
                // ANNUAL, SICK, PUBLIC: 소정 근무 전체 시간 (휴게시간 미차감)
                startTime = LocalDateTime.of(date, workStart);
                endTime = LocalDateTime.of(date, workEnd);
            }
        }

        return WorkRecord.builder()
                .memberId(memberId)
                .bizDate(date)
                .startTime(startTime)
                .endTime(endTime)
                .workType(WorkType.ANNUAL_LEAVE)
                .build();
    }
}
