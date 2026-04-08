package dev.businesstrip.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BusinessTripError implements CommonError {

    BUSINESS_TRIP_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40431", "출장 신청 내역이 없습니다."),
    INVALID_DATE_RANGE(HttpStatusCode.BAD_REQUEST, "40031", "출장 종료일이 시작일보다 빠를 수 없습니다."),
    ALREADY_PROCESSED(HttpStatusCode.CONFLICT, "40931", "이미 승인 또는 반려된 출장 신청입니다."),
    UNAUTHORIZED_ACCESS(HttpStatusCode.FORBIDDEN, "40331", "본인의 출장 신청만 조회할 수 있습니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
