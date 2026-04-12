package dev.workschedule.exception;

import dev.common.exception.ServiceException;

public class WorkScheduleException extends ServiceException {

    public WorkScheduleException(WorkScheduleError workScheduleError) {
        super(workScheduleError);
    }
}
