package dev.payrollpolicy.exception;

import dev.common.exception.ServiceException;

public class PayrollPolicyException extends ServiceException {

    public PayrollPolicyException(PayrollPolicyError error) {
        super(error);
    }
}
