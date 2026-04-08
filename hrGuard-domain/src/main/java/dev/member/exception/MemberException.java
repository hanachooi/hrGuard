package dev.member.exception;

import dev.common.exception.CommonError;
import dev.common.exception.ServiceException;

public class MemberException extends ServiceException {

    public MemberException(CommonError commonError) {
        super(commonError);
    }
}
