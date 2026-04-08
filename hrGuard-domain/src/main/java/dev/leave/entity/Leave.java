package dev.leave.entity;

import dev.common.BaseEntity;
import dev.leave.constant.LeaveStatus;
import dev.leave.constant.LeaveType;
import dev.leave.exception.LeaveError;
import dev.leave.exception.LeaveException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 휴가 신청 엔티티.
 *
 * <p>승인(APPROVED) 시 신청 기간의 각 날짜에 대해
 * {@link dev.payroll.entity.WorkRecord}(ANNUAL_LEAVE 유형)가 자동 생성됩니다.</p>
 *
 * <h3>반차 신청 제약</h3>
 * HALF_AM / HALF_PM은 단일 날짜만 허용 (startDate == endDate).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "leaves",
        indexes = @Index(name = "idx_leaves_member", columnList = "member_id, start_date"))
public class Leave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    // ── 팩토리 ───────────────────────────────────────────────────────────────

    public static Leave apply(Long memberId, LocalDate startDate, LocalDate endDate,
                              LeaveType leaveType, String reason) {
        if (endDate.isBefore(startDate)) {
            throw new LeaveException(LeaveError.INVALID_DATE_RANGE);
        }
        if ((leaveType == LeaveType.HALF_AM || leaveType == LeaveType.HALF_PM)
                && !startDate.equals(endDate)) {
            throw new LeaveException(LeaveError.HALF_DAY_MUST_BE_SINGLE_DATE);
        }
        return Leave.builder()
                .memberId(memberId)
                .startDate(startDate)
                .endDate(endDate)
                .leaveType(leaveType)
                .reason(reason)
                .build();
    }

    // ── 도메인 행동 ──────────────────────────────────────────────────────────

    public void approve() {
        if (this.status != LeaveStatus.PENDING) {
            throw new LeaveException(LeaveError.ALREADY_PROCESSED);
        }
        this.status = LeaveStatus.APPROVED;
    }

    public void reject(String reason) {
        if (this.status != LeaveStatus.PENDING) {
            throw new LeaveException(LeaveError.ALREADY_PROCESSED);
        }
        this.status = LeaveStatus.REJECTED;
        this.rejectReason = reason;
    }

    /**
     * 연차 잔여일수 차감 일수.
     * HALF_AM / HALF_PM = 0.5일, 그 외 = 신청 기간 내 일수 × 1.0.
     */
    public double getDeductionDays() {
        if (!leaveType.deductsBalance()) return 0.0;
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return days * leaveType.daysPerUnit();
    }
}
