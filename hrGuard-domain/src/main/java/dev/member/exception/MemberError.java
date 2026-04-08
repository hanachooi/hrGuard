package dev.member.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberError implements CommonError {

    MEMBER_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40401", "존재하지 않는 회원입니다."),
    EMAIL_NOT_FOUND(HttpStatusCode.NOT_FOUND, "40402", "존재하지 않는 회원 이메일입니다."),
    EMAIL_DUPLICATED(HttpStatusCode.BAD_REQUEST, "40003", "이미 등록된 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatusCode.BAD_REQUEST, "40004", "이미 사용 중인 닉네임입니다."),
    PASSWORD_NOT_MATCHED(HttpStatusCode.BAD_REQUEST, "40005", "비밀번호가 일치하지 않습니다."),
    UNAUTHORIZED_ACCESS(HttpStatusCode.FORBIDDEN, "40301", "접근 권한이 없습니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
