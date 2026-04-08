package dev.fieldwork.entity;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.common.BaseEntity;
import dev.fieldwork.exception.FieldWorkError;
import dev.fieldwork.exception.FieldWorkException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 외근 신청 엔티티.
 *
 * <p>승인(APPROVED) 시 신청한 날짜/시간에 대해
 * {@link dev.payroll.entity.WorkRecord}(FIELD 유형)가 자동 생성됩니다.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "field_work",
        indexes = @Index(name = "idx_field_work_member", columnList = "member_id"))
public class FieldWork extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "location", nullable = false, length = 200)
    private String location;

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessTripStatus status = BusinessTripStatus.PENDING;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    public static FieldWork apply(Long memberId, LocalDate workDate,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  String location, String purpose) {
        if (!endTime.isAfter(startTime)) {
            throw new FieldWorkException(FieldWorkError.INVALID_TIME_RANGE);
        }
        if (!workDate.equals(startTime.toLocalDate())) {
            throw new FieldWorkException(FieldWorkError.DATE_MISMATCH);
        }
        return FieldWork.builder()
                .memberId(memberId)
                .workDate(workDate)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .purpose(purpose)
                .build();
    }

    public void approve() {
        if (this.status != BusinessTripStatus.PENDING) {
            throw new FieldWorkException(FieldWorkError.ALREADY_PROCESSED);
        }
        this.status = BusinessTripStatus.APPROVED;
    }

    public void reject(String reason) {
        if (this.status != BusinessTripStatus.PENDING) {
            throw new FieldWorkException(FieldWorkError.ALREADY_PROCESSED);
        }
        this.status = BusinessTripStatus.REJECTED;
        this.rejectReason = reason;
    }
}
