package dev.payrollpolicy.service.response;

import dev.payrollpolicy.entity.PayrollPolicy;

public record PayrollPolicyResponse(
        Long memberId,
        int dependents,
        long nonTaxableMealAllowance
) {
    public static PayrollPolicyResponse from(PayrollPolicy policy) {
        return new PayrollPolicyResponse(
                policy.getMemberId(),
                policy.getDependents(),
                policy.getNonTaxableMealAllowance()
        );
    }
}
