package dev.fieldwork.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FieldWorkError implements CommonError {

    FIELD_WORK_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40432", "외근 신청 내역이 없습니다."),
    INVALID_TIME_RANGE(HttpStatusCode.BAD_REQUEST, "40032", "외근 종료 시각이 시작 시각보다 빠를 수 없습니다."),
    DATE_MISMATCH(HttpStatusCode.BAD_REQUEST, "40033", "외근 날짜와 시작 시각의 날짜가 일치하지 않습니다."),
    ALREADY_PROCESSED(HttpStatusCode.CONFLICT, "40932", "이미 승인 또는 반려된 외근 신청입니다."),
    UNAUTHORIZED_ACCESS(HttpStatusCode.FORBIDDEN, "40332", "본인의 외근 신청만 조회할 수 있습니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
