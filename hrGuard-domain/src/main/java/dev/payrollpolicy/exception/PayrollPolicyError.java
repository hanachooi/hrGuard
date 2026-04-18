package dev.payrollpolicy.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayrollPolicyError implements CommonError {

    PAYROLL_POLICY_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40471", "급여 정산 정책이 없습니다."),
    PAYROLL_POLICY_ALREADY_EXISTS(HttpStatusCode.CONFLICT, "40971", "이미 급여 정산 정책이 등록되어 있습니다."),
    INVALID_DEPENDENTS(HttpStatusCode.BAD_REQUEST, "40071", "부양가족 수는 1 이상이어야 합니다."),
    INVALID_NON_TAXABLE_MEAL_ALLOWANCE(HttpStatusCode.BAD_REQUEST, "40072", "식대 비과세 금액은 0원 이상이어야 합니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
