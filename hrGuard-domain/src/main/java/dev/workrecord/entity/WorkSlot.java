package dev.workrecord.entity;

import dev.common.BaseEntity;
import dev.workrecord.constant.WorkType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 단일 근무 시간 세그먼트.
 *
 * <p>{@link WorkRecord}(하루 마스터 레코드)에 종속되며,
 * 하루 안에서 근무 타입별로 여러 개 존재할 수 있습니다.</p>
 *
 * <h3>생성 경로</h3>
 * <pre>
 *   OFFICE       ← 익일 배치(WorkRecordComputeProcessor)
 *   FIELD        ← 외근 승인 즉시
 *   REMOTE       ← 재택 승인 즉시
 *   BUSINESS_TRIP← 출장 승인 즉시
 *   ANNUAL_LEAVE ← 휴가 승인 즉시
 * </pre>
 *
 * <h3>충돌 방지</h3>
 * <p>승인 기반 타입(OFFICE 제외)은 WorkRecordService가 슬롯 점유 여부를
 * 검사한 후 추가합니다. OFFICE는 배치가 blocked 구간을 차집합으로 처리하므로
 * 항상 빈 슬롯에만 채워집니다.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "work_slot",
        indexes = @Index(name = "idx_work_slot_work_record_id", columnList = "work_record_id")
)
public class WorkSlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 하루 마스터 레코드 ID.
     *
     * <p>JPA 연관 객체 대신 ID 참조를 사용하여 불필요한 WorkRecord 로딩을 방지합니다.</p>
     */
    @Column(name = "work_record_id", nullable = false)
    private Long workRecordId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 20)
    private WorkType workType;

    // ── 팩토리 ──────────────────────────────────────────────────────────────────

    /**
     * WorkRecord 에 속하는 WorkSlot 을 생성합니다.
     * WorkRecord.addSlot() 내부에서만 호출하세요.
     */
    static WorkSlot create(Long workRecordId,
                           LocalDateTime startTime,
                           LocalDateTime endTime,
                           WorkType workType) {
        return WorkSlot.builder()
                .workRecordId(workRecordId)
                .startTime(startTime)
                .endTime(endTime)
                .workType(workType)
                .build();
    }

    // ── 도메인 ──────────────────────────────────────────────────────────────────

    /**
     * 슬롯 지속 시간 (분).
     */
    public long durationMinutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }
}
