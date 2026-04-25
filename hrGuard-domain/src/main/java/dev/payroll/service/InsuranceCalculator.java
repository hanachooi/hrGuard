package dev.payroll.service;

import dev.payroll.constant.InsuranceType;
import dev.payroll.entity.InsuranceRate;
import dev.payroll.repository.InsuranceRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InsuranceCalculator {

    private final InsuranceRateRepository insuranceRateRepository;

    private Map<InsuranceType, InsuranceRate> rateMap = new EnumMap<>(InsuranceType.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN = BigDecimal.valueOf(10);

    private static BigDecimal truncateTo10(BigDecimal amount) {
        return amount.divideToIntegralValue(TEN).multiply(TEN);
    }

    /**
     * Job 종료 시 1회 호출. 메모리에 적재된 4대보험 요율을 제거합니다.
     */
    public void clear() {
        rateMap.clear();
    }

    /**
     * Job 시작 시 1회 호출. 해당 기준일 기준 4대보험 요율을 메모리에 적재합니다.
     */
    public void load(LocalDate payrollDate) {
        rateMap = Arrays.stream(InsuranceType.values())
                .collect(Collectors.toMap(
                        type -> type,
                        type -> insuranceRateRepository
                                .findFirstByTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(type, payrollDate)
                                .orElseThrow(() -> new IllegalStateException(
                                        "InsuranceRate 미등록: type=" + type + ", date=" + payrollDate)),
                        (a, b) -> a,
                        () -> new EnumMap<>(InsuranceType.class)
                ));
    }

    /**
     * @param monthlyTaxableIncome 월 과세대상 급여 (총급여 - 비과세 항목)
     * @return 4대보험 근로자 부담분 (각 항목 10원 단위 절사)
     */
    public InsuranceDeductionResult calculate(BigDecimal monthlyTaxableIncome) {
        InsuranceRate pensionRate = getRate(InsuranceType.NATIONAL_PENSION);
        InsuranceRate healthRate = getRate(InsuranceType.HEALTH);
        InsuranceRate careRate = getRate(InsuranceType.CARE);
        InsuranceRate employmentRate = getRate(InsuranceType.EMPLOYMENT);

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

    private InsuranceRate getRate(InsuranceType type) {
        InsuranceRate rate = rateMap.get(type);
        if (rate == null) {
            throw new IllegalStateException(
                    "InsuranceRate 미적재: type=" + type + " — beforeJob에서 load()를 먼저 호출하세요");
        }
        return rate;
    }
}
