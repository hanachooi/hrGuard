package dev.leave.entity;

import dev.common.BaseEntity;
import dev.leave.exception.LeaveError;
import dev.leave.exception.LeaveException;
import jakarta.persistence.*;
import lombok.*;

/**
 * 연차 잔여일수 엔티티.
 *
 * <p>사원(memberId) + 연도(year) 단위로 1건씩 관리합니다.
 * 연차·반차 승인 시 {@link #deduct(double)}로 차감되고,
 * HR이 {@link #grant(double)}로 추가 부여할 수 있습니다.</p>
 *
 * <h3>기본 부여 (근로기준법 제60조)</h3>
 * 서비스 레이어에서 getOrCreate 방식으로 초기화 시 15일이 기본값입니다.
 * 실제 운영에서는 입사일·근속연수에 따라 HR이 조정합니다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "leave_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_leave_balance_member_year",
                columnNames = {"member_id", "year"}))
public class LeaveBalance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int year;

    /**
     * 해당 연도에 부여된 총 연차 일수
     */
    @Column(name = "total_days", nullable = false)
    private double totalDays;

    /**
     * 사용 완료된 연차 일수 (승인 시점에 차감)
     */
    @Column(name = "used_days", nullable = false)
    private double usedDays;

    // ── 팩토리 ───────────────────────────────────────────────────────────────

    /**
     * 기본 15일로 연차 잔여 레코드를 초기화합니다.
     */
    public static LeaveBalance init(Long memberId, int year) {
        return LeaveBalance.builder()
                .memberId(memberId)
                .year(year)
                .totalDays(15.0)
                .usedDays(0.0)
                .build();
    }

    // ── 도메인 행동 ──────────────────────────────────────────────────────────

    /**
     * 연차 잔여일수에서 {@code days}만큼 차감합니다. 잔여 부족 시 예외.
     */
    public void deduct(double days) {
        if (getRemainingDays() < days) {
            throw new LeaveException(LeaveError.INSUFFICIENT_BALANCE);
        }
        this.usedDays += days;
    }

    /**
     * HR이 연차를 추가 부여합니다.
     */
    public void grant(double days) {
        if (days <= 0) {
            throw new LeaveException(LeaveError.INVALID_GRANT_DAYS);
        }
        this.totalDays += days;
    }

    public double getRemainingDays() {
        return totalDays - usedDays;
    }
}
