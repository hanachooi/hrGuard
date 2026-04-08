package dev.attendance.fieldwork.service.response;

import dev.attendance.fieldwork.entity.FieldWork;
import dev.fieldwork.constant.FieldWorkStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FieldWorkResponse(
        Long id,
        LocalDate workDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        String purpose,
        FieldWorkStatus status,
        String rejectReason
) {
    public static FieldWorkResponse from(FieldWork fieldWork) {
        return new FieldWorkResponse(
                fieldWork.getId(),
                fieldWork.getWorkDate(),
                fieldWork.getStartTime(),
                fieldWork.getEndTime(),
                fieldWork.getLocation(),
                fieldWork.getPurpose(),
                fieldWork.getStatus(),
                fieldWork.getRejectReason()
        );
    }
}
