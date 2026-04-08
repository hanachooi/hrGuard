package dev.workrecord.entity;

import dev.common.BaseEntity;
import dev.workrecord.constant.WorkType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 근무 기록 — 급여 계산의 실제 기준 데이터.
 *
 * <p>{@code Commute}(출입 기록)와 분리된 개념입니다.</p>
 * <ul>
 *   <li>{@code Commute}  : 물리적 사무실 출입 (출입 통제 시스템 기반, API가 관리)</li>
 *   <li>{@code WorkRecord}: 실제 근무 시간 (급여 계산 기준, 배치가 소비)</li>
 * </ul>
 *
 * <h3>하루 다건 허용</h3>
 * <p>외근·출장·재택 등으로 같은 날 여러 근무 세그먼트가 존재할 수 있습니다.
 * {@code (member_id, biz_date)}에 유니크 제약을 두지 않으며,
 * 급여 배치에서 동일 날짜의 세그먼트를 모두 합산하여 계산합니다.</p>
 *
 * <h3>근무 기록 생성 경로</h3>
 * <pre>
 *   OFFICE       ← Commute 체크인/아웃 → 자동 생성
 *   FIELD        ← 외근 신청 승인 → 생성
 *   REMOTE       ← 재택 신청 승인 → 생성
 *   BUSINESS_TRIP← 출장 신청 승인 → 생성
 * </pre>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "work_record",
        indexes = @Index(name = "idx_work_record_member_biz_date", columnList = "member_id, biz_date")
)
public class WorkRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * 논리 근무일 (야간 교대 등 고려한 비즈니스 날짜)
     */
    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 근무 종료 시각. null = 아직 종료 안 됨
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false)
    private WorkType workType;

    public void recordEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
