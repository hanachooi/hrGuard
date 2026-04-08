package dev.commute.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommuteError implements CommonError {

    COMMUTE_ALREADY(HttpStatusCode.BAD_REQUEST, "40021", "이미 출근 중입니다. 퇴근 후 다시 출근할 수 있습니다."),
    CHECKOUT_ALREADY(HttpStatusCode.BAD_REQUEST, "40022", "이미 퇴근했습니다."),
    COMMUTE_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40421", "진행 중인 출근 기록이 없습니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
