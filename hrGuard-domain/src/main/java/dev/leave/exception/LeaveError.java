package dev.leave.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LeaveError implements CommonError {

    ANNUAL_LEAVE_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40432", "휴가 신청 내역이 없습니다."),
    INVALID_DATE_RANGE(HttpStatusCode.BAD_REQUEST, "40032", "휴가 종료 일시가 시작 일시보다 빠를 수 없습니다."),
    ALREADY_PROCESSED(HttpStatusCode.CONFLICT, "40932", "이미 승인 또는 반려된 휴가 신청입니다."),
    INSUFFICIENT_BALANCE(HttpStatusCode.BAD_REQUEST, "40034", "연차 잔여일수가 부족합니다."),
    INVALID_GRANT_DAYS(HttpStatusCode.BAD_REQUEST, "40035", "부여 일수는 0보다 커야 합니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
