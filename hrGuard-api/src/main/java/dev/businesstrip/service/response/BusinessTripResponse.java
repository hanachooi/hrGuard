package dev.attendance.businesstrip.service.response;

import dev.attendance.businesstrip.constant.BusinessTripStatus;
import dev.attendance.businesstrip.entity.BusinessTrip;

import java.time.LocalDate;

public record BusinessTripResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String destination,
        String purpose,
        BusinessTripStatus status,
        String rejectReason
) {
    public static BusinessTripResponse from(BusinessTrip trip) {
        return new BusinessTripResponse(
                trip.getId(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getDestination(),
                trip.getPurpose(),
                trip.getStatus(),
                trip.getRejectReason()
        );
    }
}
