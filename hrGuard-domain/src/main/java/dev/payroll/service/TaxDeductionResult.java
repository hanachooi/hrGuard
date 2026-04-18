package dev.payroll.service;

/**
 * 근로소득세 원천징수 계산 결과.
 *
 * <ul>
 *   <li>근로소득세  : 간이세액표 산식 적용, 1,000원 미만 절사</li>
 *   <li>지방소득세  : 근로소득세 × 10%, 100원 미만 절사</li>
 * </ul>
 */
public record TaxDeductionResult(
        long incomeTax,
        long localIncomeTax
) {
    public long total() {
        return incomeTax + localIncomeTax;
    }
}
