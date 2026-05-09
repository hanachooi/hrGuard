package dev.payroll.service;

import dev.payroll.repository.SimplifiedTaxRepository;
import dev.payroll.repository.projection.SimplifiedTaxProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class TaxCalculator {

    // 11명 까지만 커버, 11만 초과는 별도의 로직이 존재한다고함(현실적으로 고려 X)
    private static final int MAX_DEPENDENTS = 11;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal LOCAL_TAX_RATE = new BigDecimal("0.1");

    private final SimplifiedTaxRepository simplifiedTaxRepository;

    // salaryMin → projection. floorEntry 로 구간 조회.
    private NavigableMap<Integer, SimplifiedTaxProjection> taxTable = new TreeMap<>();

    private static BigDecimal truncateTo100(BigDecimal amount) {
        return amount.divideToIntegralValue(HUNDRED).multiply(HUNDRED);
    }

    /**
     * Job 종료 시 1회 호출. 메모리에 적재된 간이세액표를 제거합니다.
     */
    public void clear() {
        taxTable.clear();
    }

    /**
     * Job 시작 시 1회 호출. 해당 기준일에 유효한 간이세액표를 메모리에 적재합니다.
     */
    public void load(LocalDate payrollDate) {
        List<SimplifiedTaxProjection> rows = simplifiedTaxRepository.findAllByEffectiveDate(payrollDate);
        if (rows.isEmpty()) {
            throw new IllegalStateException("SimplifiedTax 미등록: date=" + payrollDate);
        }
        NavigableMap<Integer, SimplifiedTaxProjection> map = new TreeMap<>();
        for (SimplifiedTaxProjection row : rows) {
            map.put(row.salaryMin(), row);
        }
        taxTable = map;
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
        BigDecimal incomeTax = lookup(salaryInThousands)
                .map(row -> BigDecimal.valueOf(row.getTaxByDependents(clampedDependents)))
                .orElse(BigDecimal.ZERO);

        // 지방소득세: 근로소득세 × 10%, 100원 미만 절사
        BigDecimal localIncomeTax = truncateTo100(
                incomeTax.multiply(LOCAL_TAX_RATE).setScale(0, RoundingMode.HALF_UP));

        return new TaxDeductionResult(incomeTax, localIncomeTax);
    }

    private java.util.Optional<SimplifiedTaxProjection> lookup(int salaryInThousands) {
        Map.Entry<Integer, SimplifiedTaxProjection> entry = taxTable.floorEntry(salaryInThousands);
        if (entry == null) {
            return java.util.Optional.empty();
        }
        SimplifiedTaxProjection row = entry.getValue();
        // salaryMax 는 상한 (exclusive 매칭 — 기존 쿼리 조건 유지)
        if (salaryInThousands >= row.salaryMax()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(row);
    }
}
