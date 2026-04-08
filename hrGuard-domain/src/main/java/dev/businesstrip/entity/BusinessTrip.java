package dev.businesstrip.entity;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.businesstrip.exception.BusinessTripError;
import dev.businesstrip.exception.BusinessTripException;
import dev.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 출장 신청 엔티티.
 *
 * <p>승인(APPROVED) 시 출장 기간 내 각 날짜에 대해
 * {@link dev.payroll.entity.WorkRecord}(BUSINESS_TRIP 유형)가 자동 생성됩니다.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "business_trip",
        indexes = @Index(name = "idx_business_trip_member", columnList = "member_id"))
public class BusinessTrip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

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

    public static BusinessTrip apply(Long memberId, LocalDate startDate, LocalDate endDate,
                                     String destination, String purpose) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessTripException(BusinessTripError.INVALID_DATE_RANGE);
        }
        return BusinessTrip.builder()
                .memberId(memberId)
                .startDate(startDate)
                .endDate(endDate)
                .destination(destination)
                .purpose(purpose)
                .build();
    }

    public void approve() {
        if (this.status != BusinessTripStatus.PENDING) {
            throw new BusinessTripException(BusinessTripError.ALREADY_PROCESSED);
        }
        this.status = BusinessTripStatus.PENDING;
        ;
    }

    public void reject(String reason) {
        if (this.status != BusinessTripStatus.PENDING) {
            throw new BusinessTripException(BusinessTripError.ALREADY_PROCESSED);
        }
        this.status = BusinessTripStatus.REJECTED;
        this.rejectReason = reason;
    }
}
