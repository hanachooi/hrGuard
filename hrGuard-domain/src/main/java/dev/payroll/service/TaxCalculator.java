package dev.payroll.service;

import dev.payroll.repository.SimplifiedTaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TaxCalculator {

    // 11명 까지만 커버, 11만 초과는 별도의 로직이 존재한다고함(현실적으로 고려 X)
    private static final int MAX_DEPENDENTS = 11;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal LOCAL_TAX_RATE = new BigDecimal("0.1");

    private final SimplifiedTaxRepository simplifiedTaxRepository;

    private static BigDecimal truncateTo100(BigDecimal amount) {
        return amount.divideToIntegralValue(HUNDRED).multiply(HUNDRED);
    }

    /**
     * @param monthlyTaxableIncome 월 과세대상 급여 (원)
     * @param dependents           공제대상 가족 수 (본인 포함, 최소 1)
     * @param payrollDate          정산 기준일 (해당 월 1일)
     * @return 근로소득세 + 지방소득세 원천징수액 (100% 기준)
     */
    public TaxDeductionResult calculate(BigDecimal monthlyTaxableIncome, int dependents, LocalDate payrollDate) {
        int clampedDependents = Math.min(Math.max(dependents, 1), MAX_DEPENDENTS);

        // 간이세액표 급여 단위가 천원이므로 변환 (절사)
        int salaryInThousands = monthlyTaxableIncome.divideToIntegralValue(BigDecimal.valueOf(1000)).intValue();

        // 비과세구간 존재(국세청 간이세액표 기반) → 테이블에 없으면 0원 처리
        BigDecimal incomeTax = simplifiedTaxRepository
                .findTax(salaryInThousands, payrollDate)
                .map(row -> BigDecimal.valueOf(row.getTaxByDependents(clampedDependents)))
                .orElse(BigDecimal.ZERO);

        // 지방소득세: 근로소득세 × 10%, 100원 미만 절사
        BigDecimal localIncomeTax = truncateTo100(
                incomeTax.multiply(LOCAL_TAX_RATE).setScale(0, RoundingMode.HALF_UP));

        return new TaxDeductionResult(incomeTax, localIncomeTax);
    }
}
