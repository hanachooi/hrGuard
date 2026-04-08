package dev.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공통 에러코드 (BAD_REQUEST 등)
@Getter
@RequiredArgsConstructor
public enum ErrorCode implements CommonError {

    BAD_REQUEST(HttpStatusCode.BAD_REQUEST, "400", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatusCode.INTERNAL_SERVER_ERROR, "500", "내부 서버 오류가 발생했습니다. 관리자에게 문의해주세요."),
    METHOD_NOT_ALLOWED(HttpStatusCode.METHOD_NOT_ALLOWED, "405", "지원하지 않는 Http Method 입니다."),
    INVALID_PARAMETER_TYPE(HttpStatusCode.BAD_REQUEST, "400", "잘못된 파라미터 타입입니다."),
    UNAUTHORIZED(HttpStatusCode.UNAUTHORIZED, "401", "인증이 필요합니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
