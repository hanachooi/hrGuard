package dev.payrollpolicy.service.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record PayrollPolicyUpsertRequest(

        @Min(value = 1, message = "부양가족 수는 1 이상이어야 합니다.")
        int dependents,

        @PositiveOrZero(message = "식대 비과세 금액은 0원 이상이어야 합니다.")
        long nonTaxableMealAllowance
) {
}
