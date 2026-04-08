package dev.commute.exception;

import dev.common.exception.CommonError;
import dev.common.exception.ServiceException;

public class CommuteException extends ServiceException {

    public CommuteException(CommonError commonError) {
        super(commonError);
    }
}
