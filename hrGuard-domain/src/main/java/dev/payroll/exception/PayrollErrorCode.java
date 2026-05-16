package dev.payroll.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayrollErrorCode implements CommonError {

    MONTHLY_PAYROLL_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40481", "급여 정산 내역이 존재하지 않습니다."),
    PAYROLL_ITEM_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40482", "급여 항목이 존재하지 않습니다."),
    MONTHLY_PAYROLL_ALREADY_EXISTS(HttpStatusCode.CONFLICT, "40981", "해당 기간의 급여 정산이 이미 존재합니다."),
    INVALID_STATUS_TRANSITION(HttpStatusCode.BAD_REQUEST, "40081", "유효하지 않은 급여 정산 상태 전환입니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
