package dev.payroll.entity;

import dev.common.BaseEntity;
import dev.payroll.constant.PayrollStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

// 월별 급여 집계 테이블 (배치가 매월 말 생성)
// status: DRAFT → CONFIRMED → PAID
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "year", "month"}))
public class MonthlyPayroll extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    // 해당 월 총 지급액 (원) — 수당 합계 (공제 전)
    @Builder.Default
    @Column(name = "total_amount", nullable = false)
    private long totalAmount = 0L;

    // ── 4대보험 근로자 부담분 ─────────────────────────────────────────────
    @Builder.Default
    @Column(name = "national_pension", nullable = false)
    private long nationalPension = 0L;

    @Builder.Default
    @Column(name = "health_insurance", nullable = false)
    private long healthInsurance = 0L;

    @Builder.Default
    @Column(name = "long_term_care", nullable = false)
    private long longTermCare = 0L;

    @Builder.Default
    @Column(name = "employment_insurance", nullable = false)
    private long employmentInsurance = 0L;

    // ── 세금 ──────────────────────────────────────────────────────────────
    @Builder.Default
    @Column(name = "income_tax", nullable = false)
    private long incomeTax = 0L;

    @Builder.Default
    @Column(name = "local_income_tax", nullable = false)
    private long localIncomeTax = 0L;

    // ── 집계 ──────────────────────────────────────────────────────────────
    /**
     * 총 공제액 = 4대보험 합계 + 근로소득세 + 지방소득세
     */
    @Builder.Default
    @Column(name = "total_deduction", nullable = false)
    private long totalDeduction = 0L;

    /**
     * 실수령액 = 총 지급액 - 총 공제액
     */
    @Builder.Default
    @Column(name = "net_pay", nullable = false)
    private long netPay = 0L;

    // ── 주 52시간 한도 초과 여부 (근로기준법 제53조) ──────────────────────────
    /**
     * 해당 월 내 1주라도 연장근로 12시간을 초과한 주가 있으면 true
     */
    @Builder.Default
    @Column(name = "overtime_limit_exceeded", nullable = false)
    private boolean overtimeLimitExceeded = false;

    /**
     * 해당 월에서 주간 연장근로 최대 시간.
     * 12h 초과 여부 판단 및 관리자 확인용.
     * 예) 14.5 → 해당 주에 14.5h 연장근로 발생 (2.5h 법정 한도 초과)
     */
    @Builder.Default
    @Column(name = "max_weekly_overtime_hours", nullable = false)
    private double maxWeeklyOvertimeHours = 0.0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus status = PayrollStatus.DRAFT;

    // DB 컬럼 아님 — 배치 processor → writer 간 items 전달용
    @Builder.Default
    @Transient
    private List<PayrollItem> pendingItems = new ArrayList<>();

    public void updateTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void updateDeductions(
            long nationalPension,
            long healthInsurance,
            long longTermCare,
            long employmentInsurance,
            long incomeTax,
            long localIncomeTax
    ) {
        this.nationalPension = nationalPension;
        this.healthInsurance = healthInsurance;
        this.longTermCare = longTermCare;
        this.employmentInsurance = employmentInsurance;
        this.incomeTax = incomeTax;
        this.localIncomeTax = localIncomeTax;
        this.totalDeduction = nationalPension + healthInsurance + longTermCare
                + employmentInsurance + incomeTax + localIncomeTax;
        this.netPay = this.totalAmount - this.totalDeduction;
    }

    public void updateOvertimeCheck(boolean overtimeLimitExceeded, double maxWeeklyOvertimeHours) {
        this.overtimeLimitExceeded = overtimeLimitExceeded;
        this.maxWeeklyOvertimeHours  = maxWeeklyOvertimeHours;
    }

    public List<PayrollItem> getPendingItems() {
        return pendingItems;
    }

    public void setPendingItems(List<PayrollItem> items) {
        this.pendingItems = items;
    }
}
