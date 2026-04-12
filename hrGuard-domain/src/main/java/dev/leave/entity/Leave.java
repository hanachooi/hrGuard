package dev.leave.entity;

import dev.common.BaseEntity;
import dev.leave.constant.LeaveStatus;
import dev.leave.constant.LeaveType;
import dev.leave.exception.LeaveError;
import dev.leave.exception.LeaveException;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 휴가 신청 엔티티.
 *
 * <p>출장과 동일하게 startDateTime~endDateTime 시간 범위로 관리합니다.
 * 반차·반반차는 유형 구분 없이 시간 범위로 표현됩니다.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "leaves",
        indexes = @Index(name = "idx_leaves_member", columnList = "member_id, start_date_time"))
public class Leave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

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

    public static Leave apply(Long memberId,
                              LocalDateTime startDateTime, LocalDateTime endDateTime,
                              LeaveType leaveType, String reason) {
        if (!endDateTime.isAfter(startDateTime)) {
            throw new LeaveException(LeaveError.INVALID_DATE_RANGE);
        }
        return Leave.builder()
                .memberId(memberId)
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
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
     * 연차 잔여일수 차감량 — 8시간 = 1일 기준.
     * 예) 4시간(반차) → 0.5일, 2시간(반반차) → 0.25일
     */
    public double getDeductionDays() {
        if (!leaveType.deductsBalance()) return 0.0;
        long minutes = Duration.between(startDateTime, endDateTime).toMinutes();
        return minutes / (8.0 * 60.0);
    }
}
