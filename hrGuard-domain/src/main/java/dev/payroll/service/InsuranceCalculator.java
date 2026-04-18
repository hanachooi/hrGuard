package dev.payroll.service;

import dev.payroll.constant.InsuranceType;
import dev.payroll.entity.InsuranceRate;
import dev.payroll.repository.InsuranceRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class InsuranceCalculator {

    private final InsuranceRateRepository insuranceRateRepository;

    private static long truncateTo10(long amount) {
        return (amount / 10) * 10;
    }

    /**
     * @param monthlyTaxableIncome 월 과세대상 급여 (총급여 - 비과세 항목)
     * @param payrollDate          정산 기준일 (해당 월 1일)
     * @return 4대보험 근로자 부담분 (각 항목 10원 단위 절사)
     */
    public InsuranceDeductionResult calculate(long monthlyTaxableIncome, LocalDate payrollDate) {
        InsuranceRate pensionRate = findRate(InsuranceType.NATIONAL_PENSION, payrollDate);
        InsuranceRate healthRate = findRate(InsuranceType.HEALTH, payrollDate);
        InsuranceRate careRate = findRate(InsuranceType.CARE, payrollDate);
        InsuranceRate employmentRate = findRate(InsuranceType.EMPLOYMENT, payrollDate);

        // 국민연금: 기준소득월액 상·하한 적용
        long pensionBase = monthlyTaxableIncome;
        if (pensionRate.getIncomeMin() != null) {
            pensionBase = Math.max(pensionBase, pensionRate.getIncomeMin());
        }
        if (pensionRate.getIncomeMax() != null) {
            pensionBase = Math.min(pensionBase, pensionRate.getIncomeMax());
        }
        // DB에 요율은 % 단위로 저장 (예: 4.5000 = 4.5%) → 계산 시 /100
        long nationalPension = truncateTo10(Math.round(pensionBase * pensionRate.getEmployeeRate().doubleValue() / 100));

        long healthInsurance = truncateTo10(Math.round(monthlyTaxableIncome * healthRate.getEmployeeRate().doubleValue() / 100));

        // 장기요양: 건강보험료에 요율 적용 (CARE employee_rate = 건강보험료 대비 %)
        long longTermCare = truncateTo10(Math.round(healthInsurance * careRate.getEmployeeRate().doubleValue() / 100));

        long employmentInsurance = truncateTo10(Math.round(monthlyTaxableIncome * employmentRate.getEmployeeRate().doubleValue() / 100));

        return new InsuranceDeductionResult(nationalPension, healthInsurance, longTermCare, employmentInsurance);
    }

    private InsuranceRate findRate(InsuranceType type, LocalDate date) {
        return insuranceRateRepository
                .findFirstByTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(type, date)
                .orElseThrow(() -> new IllegalStateException(
                        "InsuranceRate 미등록: type=" + type + ", date=" + date));
    }
}
