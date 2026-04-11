package dev.workrecord.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkRecordError implements CommonError {

    SLOT_CONFLICT(HttpStatusCode.CONFLICT, "40951",
            "해당 시간대는 이미 다른 근무 유형이 점유하고 있습니다."),
    WORK_RECORD_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40451",
            "해당 날짜의 근무 기록이 없습니다."),
    CANNOT_DELETE_OFFICE_SLOT(HttpStatusCode.BAD_REQUEST, "40051",
            "OFFICE 슬롯은 직접 삭제할 수 없습니다. 배치 재처리를 이용하세요.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
