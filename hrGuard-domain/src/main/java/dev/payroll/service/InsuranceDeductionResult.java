package dev.payroll.service;

import java.math.BigDecimal;

/**
 * 4대보험 근로자 부담분 계산 결과 (2025년 기준).
 *
 * <ul>
 *   <li>국민연금  : 기준소득월액 × 4.5% (상한 617만원, 하한 39만원)</li>
 *   <li>건강보험  : 과세소득 × 3.545%</li>
 *   <li>장기요양  : 건강보험료 × 6.475%</li>
 *   <li>고용보험  : 과세소득 × 0.9%</li>
 *   <li>산재보험  : 근로자 부담 없음 (사업주 전액)</li>
 * </ul>
 *
 * <p>각 항목은 10원 단위 절사 처리됩니다.</p>
 */
public record InsuranceDeductionResult(
        BigDecimal nationalPension,
        BigDecimal healthInsurance,
        BigDecimal longTermCare,
        BigDecimal employmentInsurance
) {
    public BigDecimal total() {
        return nationalPension.add(healthInsurance).add(longTermCare).add(employmentInsurance);
    }
}
