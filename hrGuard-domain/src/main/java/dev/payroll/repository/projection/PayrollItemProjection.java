package dev.payroll.repository.projection;

import com.github.f4b6a3.tsid.TsidCreator;
import dev.payroll.constant.PayrollItemType;

import java.math.BigDecimal;

/**
 * PayrollItem 배치 INSERT용 projection.
 * PayrollItem 엔티티 없이 필드값만 보유하여 영속성 컨텍스트를 거치지 않는다.
 */
public record PayrollItemProjection(
        Long id,
        Long monthlyPayrollId,
        PayrollItemType itemType,
        double hours,
        BigDecimal amount
) {
    public static PayrollItemProjection of(Long monthlyPayrollId, PayrollItemType itemType, double hours, BigDecimal amount) {
        return new PayrollItemProjection(TsidCreator.getTsid().toLong(), monthlyPayrollId, itemType, hours, amount);
    }

    public PayrollItemProjection withMonthlyPayrollId(Long newMonthlyPayrollId) {
        return new PayrollItemProjection(id, newMonthlyPayrollId, itemType, hours, amount);
    }
}
