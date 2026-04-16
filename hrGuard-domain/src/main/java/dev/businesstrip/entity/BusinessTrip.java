package dev.businesstrip.entity;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.businesstrip.exception.BusinessTripError;
import dev.businesstrip.exception.BusinessTripException;
import dev.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "business_trip")
public class BusinessTrip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "destination", nullable = false, length = 200)
    private String destination;

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessTripStatus status = BusinessTripStatus.PENDING;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    public static BusinessTrip apply(Long memberId,
                                     LocalDateTime startDateTime, LocalDateTime endDateTime,
                                     String destination, String purpose) {
        if (!endDateTime.isAfter(startDateTime)) {
            throw new BusinessTripException(BusinessTripError.INVALID_DATE_RANGE);
        }
        return BusinessTrip.builder()
                .memberId(memberId)
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
                .destination(destination)
                .purpose(purpose)
                .build();
    }

    public void approve() {
        if (this.status != BusinessTripStatus.PENDING) {
            throw new BusinessTripException(BusinessTripError.ALREADY_PROCESSED);
        }
        this.status = BusinessTripStatus.APPROVED;
    }

    public void reject(String reason) {
        if (this.status != BusinessTripStatus.PENDING) {
            throw new BusinessTripException(BusinessTripError.ALREADY_PROCESSED);
        }
        this.status = BusinessTripStatus.REJECTED;
        this.rejectReason = reason;
    }
}
