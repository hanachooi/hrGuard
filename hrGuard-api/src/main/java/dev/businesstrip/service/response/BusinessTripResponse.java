package dev.businesstrip.service.response;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.businesstrip.entity.BusinessTrip;

import java.time.LocalDateTime;

public record BusinessTripResponse(
        Long id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String destination,
        String purpose,
        BusinessTripStatus status,
        String rejectReason
) {
    public static BusinessTripResponse from(BusinessTrip trip) {
        return new BusinessTripResponse(
                trip.getId(),
                trip.getStartDateTime(),
                trip.getEndDateTime(),
                trip.getDestination(),
                trip.getPurpose(),
                trip.getStatus(),
                trip.getRejectReason()
        );
    }
}
