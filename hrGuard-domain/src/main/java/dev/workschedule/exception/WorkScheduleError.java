package dev.workschedule.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkScheduleError implements CommonError {

    WORK_SCHEDULE_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40461", "근무 일정이 없습니다."),
    WORK_SCHEDULE_ALREADY_EXISTS(HttpStatusCode.CONFLICT, "40961", "이미 근무 일정이 등록되어 있습니다."),
    INVALID_WORK_DAYS(HttpStatusCode.BAD_REQUEST, "40061", "근무 요일은 비어 있을 수 없습니다."),
    INVALID_TIME_RANGE(HttpStatusCode.BAD_REQUEST, "40062", "근무 종료 시각은 시작 시각보다 늦어야 합니다."),
    INVALID_DAILY_WORK_HOURS(HttpStatusCode.BAD_REQUEST, "40063", "일 소정 근로시간은 0보다 커야 합니다."),
    INVALID_HOURLY_WAGE(HttpStatusCode.BAD_REQUEST, "40064", "시급은 0보다 커야 합니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
