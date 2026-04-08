package dev.common.identity.exception;

import dev.common.exception.ErrorCode;
import dev.common.exception.ServiceException;

// 인증 실패 시 발생하는 예외
public class UnauthorizedException extends ServiceException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED);
    }
}
