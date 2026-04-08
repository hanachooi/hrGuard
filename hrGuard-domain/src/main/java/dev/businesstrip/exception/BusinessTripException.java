package dev.businesstrip.exception;

import dev.common.exception.ServiceException;

public class BusinessTripException extends ServiceException {
    public BusinessTripException(BusinessTripError error) {
        super(error);
    }
}
