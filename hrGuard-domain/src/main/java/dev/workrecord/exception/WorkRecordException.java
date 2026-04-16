package dev.workrecord.exception;

import dev.common.exception.CommonError;
import dev.common.exception.ServiceException;

public class WorkRecordException extends ServiceException {

    public WorkRecordException(CommonError commonError) {
        super(commonError);
    }
}
