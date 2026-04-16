package dev.fieldwork.service.response;

import dev.fieldwork.constant.FieldWorkStatus;
import dev.fieldwork.entity.FieldWork;

import java.time.LocalDateTime;

public record FieldWorkResponse(
        Long id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String location,
        String purpose,
        FieldWorkStatus status,
        String rejectReason
) {
    public static FieldWorkResponse from(FieldWork fieldWork) {
        return new FieldWorkResponse(
                fieldWork.getId(),
                fieldWork.getStartDateTime(),
                fieldWork.getEndDateTime(),
                fieldWork.getLocation(),
                fieldWork.getPurpose(),
                fieldWork.getStatus(),
                fieldWork.getRejectReason()
        );
    }
}
