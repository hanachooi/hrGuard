package dev.payroll.service;

import dev.payroll.constant.InsuranceType;
import dev.payroll.entity.InsuranceRate;
import dev.payroll.repository.InsuranceRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class InsuranceCalculator {

    private final InsuranceRateRepository insuranceRateRepository;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN = BigDecimal.valueOf(10);

    private static BigDecimal truncateTo10(BigDecimal amount) {
        return amount.divideToIntegralValue(TEN).multiply(TEN);
    }

    /**
     * @param monthlyTaxableIncome 월 과세대상 급여 (총급여 - 비과세 항목)
     * @param payrollDate          정산 기준일 (해당 월 1일)
     * @return 4대보험 근로자 부담분 (각 항목 10원 단위 절사)
     */
    public InsuranceDeductionResult calculate(BigDecimal monthlyTaxableIncome, LocalDate payrollDate) {
        InsuranceRate pensionRate = findRate(InsuranceType.NATIONAL_PENSION, payrollDate);
        InsuranceRate healthRate = findRate(InsuranceType.HEALTH, payrollDate);
        InsuranceRate careRate = findRate(InsuranceType.CARE, payrollDate);
        InsuranceRate employmentRate = findRate(InsuranceType.EMPLOYMENT, payrollDate);

        // 국민연금: 기준소득월액 상·하한 적용
        BigDecimal pensionBase = monthlyTaxableIncome;
        if (pensionRate.getIncomeMin() != null) {
            BigDecimal min = BigDecimal.valueOf(pensionRate.getIncomeMin());
            if (pensionBase.compareTo(min) < 0) pensionBase = min;
        }
        if (pensionRate.getIncomeMax() != null) {
            BigDecimal max = BigDecimal.valueOf(pensionRate.getIncomeMax());
            if (pensionBase.compareTo(max) > 0) pensionBase = max;
        }
        // DB에 요율은 % 단위로 저장 (예: 4.5000 = 4.5%) → 계산 시 /100
        BigDecimal nationalPension = truncateTo10(
                pensionBase.multiply(pensionRate.getEmployeeRate())
                        .divide(HUNDRED, 0, RoundingMode.HALF_UP));

        BigDecimal healthInsurance = truncateTo10(
                monthlyTaxableIncome.multiply(healthRate.getEmployeeRate())
                        .divide(HUNDRED, 0, RoundingMode.HALF_UP));

        // 장기요양: 건강보험료에 요율 적용 (CARE employee_rate = 건강보험료 대비 %)
        BigDecimal longTermCare = truncateTo10(
                healthInsurance.multiply(careRate.getEmployeeRate())
                        .divide(HUNDRED, 0, RoundingMode.HALF_UP));

        BigDecimal employmentInsurance = truncateTo10(
                monthlyTaxableIncome.multiply(employmentRate.getEmployeeRate())
                        .divide(HUNDRED, 0, RoundingMode.HALF_UP));

        return new InsuranceDeductionResult(nationalPension, healthInsurance, longTermCare, employmentInsurance);
    }

    private InsuranceRate findRate(InsuranceType type, LocalDate date) {
        return insuranceRateRepository
                .findFirstByTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(type, date)
                .orElseThrow(() -> new IllegalStateException(
                        "InsuranceRate 미등록: type=" + type + ", date=" + date));
    }
}
