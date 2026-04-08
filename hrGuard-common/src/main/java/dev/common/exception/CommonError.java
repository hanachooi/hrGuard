package dev.common.exception;

import jakarta.validation.Payload;

// 에러 인터페이스 (상태코드, 메시지 정의)
public interface CommonError extends Payload {

    HttpStatusCode getHttpStatus();

    String getCode();

    String getMessage();
}
