package dev.payroll.exception;

import dev.common.exception.ServiceException;

public class PayrollException extends ServiceException {

    public PayrollException(PayrollErrorCode errorCode) {
        super(errorCode);
    }

    @Override
    public PayrollErrorCode getCommonError() {
        return (PayrollErrorCode) super.getCommonError();
    }
}
