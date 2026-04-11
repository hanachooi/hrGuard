package dev.fieldwork.entity;

import dev.common.BaseEntity;
import dev.fieldwork.constant.FieldWorkStatus;
import dev.fieldwork.exception.FieldWorkError;
import dev.fieldwork.exception.FieldWorkException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 외근 신청 엔티티.
 *
 * <p>승인(APPROVED) 시 외근 구간을 날짜별 WorkRecord 슬롯으로 분할하여
 * {@link dev.workrecord.entity.WorkRecord}(FIELD 유형)가 자동 생성됩니다.</p>
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

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "location", nullable = false, length = 200)
    private String location;

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldWorkStatus status = FieldWorkStatus.PENDING;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    public static FieldWork apply(Long memberId,
                                  LocalDateTime startDateTime, LocalDateTime endDateTime,
                                  String location, String purpose) {
        if (!endDateTime.isAfter(startDateTime)) {
            throw new FieldWorkException(FieldWorkError.INVALID_TIME_RANGE);
        }
        return FieldWork.builder()
                .memberId(memberId)
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
                .location(location)
                .purpose(purpose)
                .build();
    }

    public void approve() {
        if (this.status != FieldWorkStatus.PENDING) {
            throw new FieldWorkException(FieldWorkError.ALREADY_PROCESSED);
        }
        this.status = FieldWorkStatus.APPROVED;
    }

    public void reject(String reason) {
        if (this.status != FieldWorkStatus.PENDING) {
            throw new FieldWorkException(FieldWorkError.ALREADY_PROCESSED);
        }
        this.status = FieldWorkStatus.REJECTED;
        this.rejectReason = reason;
    }
}
