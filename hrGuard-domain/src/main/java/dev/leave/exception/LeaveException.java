package dev.leave.exception;

import dev.common.exception.ServiceException;

public class LeaveException extends ServiceException {
    public LeaveException(LeaveError error) {
        super(error);
    }
}
