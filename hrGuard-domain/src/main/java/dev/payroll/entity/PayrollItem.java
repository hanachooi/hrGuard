package dev.payroll.entity;

import dev.payroll.constant.PayrollItemType;
import jakarta.persistence.*;
import lombok.*;

// 급여 상세 항목 (기본급, 연장수당, 야간수당, 휴일수당 등)
// MonthlyPayroll 1:N 구조로 정규화
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PayrollItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_payroll_id", nullable = false)
    private MonthlyPayroll monthlyPayroll;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private PayrollItemType itemType;

    // 해당 항목 근무 시간 (소수점 허용, e.g. 2.5시간)
    @Column(nullable = false)
    private double hours;

    // 해당 항목 금액 (원)
    @Column(nullable = false)
    private long amount;
}
