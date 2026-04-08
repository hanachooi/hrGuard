package dev.fieldwork.exception;

import dev.common.exception.ServiceException;

public class FieldWorkException extends ServiceException {
    public FieldWorkException(FieldWorkError error) {
        super(error);
    }
}
