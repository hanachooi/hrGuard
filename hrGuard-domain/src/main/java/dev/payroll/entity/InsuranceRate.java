package dev.payroll.entity;

import dev.common.BaseEntity;
import dev.payroll.constant.InsuranceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// 연도별 4대보험 요율 테이블 (법정 기준값)
// 국민연금은 매년 7/1 기준소득월액 상·하한이 변경되므로 effective_from으로 구분
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "insurance_rate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"insurance_type", "effective_from"})
)
public class InsuranceRate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false)
    private InsuranceType type;

    // 근로자 부담 요율 (예: 국민연금 4.5000)
    @Column(name = "employee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal employeeRate;

    // 사업주 부담 요율
    @Column(name = "employer_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal employerRate;

    // 적용 시작일 (예: 2025-01-01, 2025-07-01)
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    // 국민연금 전용 — 기준소득월액 하한 (원, null 이면 적용 안 함)
    @Column(name = "income_min")
    private Long incomeMin;

    // 국민연금 전용 — 기준소득월액 상한 (원, null 이면 적용 안 함)
    @Column(name = "income_max")
    private Long incomeMax;
}
