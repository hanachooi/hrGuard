package dev.workrecord.entity;

import dev.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 직원 1인 1일 근무 기록 — 급여 계산의 파생 집계 테이블.
 *
 * <p>원천 데이터({@code Commute}, {@code Leave}, {@code BusinessTrip}, {@code FieldWork})를
 * 기반으로 배치가 재생성하는 파생 테이블입니다.
 * 직접 수정하지 말고 WorkRecord 배치 재실행으로 갱신하세요.</p>
 *
 * <h3>집계 필드 구성</h3>
 * <pre>
 *   원천별 시간:  officeMinutes, leaveMinutes, businessTripMinutes, fieldWorkMinutes
 *   정산용 시간:  regularMinutes, overtimeMinutes, nightMinutes, holidayMinutes, holidayOvertimeMinutes
 * </pre>
 *
 * <h3>배치 계산 흐름</h3>
 * <pre>
 *   Commute(출퇴근) + Leave(휴가) + BusinessTrip(출장) + FieldWork(외근)
 *     → gap 보정 + 차집합으로 OFFICE 구간 산출
 *     → 근로기준법 기준 휴게시간 차감
 *     → 연장/야간/휴일 분류
 *     → WorkRecord 1건 upsert
 * </pre>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "work_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_work_record_member_biz_date",
                columnNames = {"member_id", "biz_date"}
        ),
        indexes = @Index(name = "idx_work_record_member_biz_date", columnList = "member_id, biz_date")
)
public class WorkRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * 논리 근무일 (야간 교대 등 고려한 비즈니스 날짜).
     */
    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    // ── 원천별 집계 (분) ─────────────────────────────────────────────────────────

    /** 사무실 근무 시간 (분) — Commute 기반, gap 보정 후 차집합으로 산출 */
    @Builder.Default
    @Column(name = "office_minutes", nullable = false)
    private int officeMinutes = 0;

    /** 휴가 시간 (분) — 승인된 Leave 기준, 휴게시간 미차감 */
    @Builder.Default
    @Column(name = "leave_minutes", nullable = false)
    private int leaveMinutes = 0;

    /** 출장 시간 (분) — 승인된 BusinessTrip 기준, 소정 근무 시간 전체 */
    @Builder.Default
    @Column(name = "business_trip_minutes", nullable = false)
    private int businessTripMinutes = 0;

    /** 외근 시간 (분) — 승인된 FieldWork 기준, 신청 시간 그대로 */
    @Builder.Default
    @Column(name = "field_work_minutes", nullable = false)
    private int fieldWorkMinutes = 0;

    // ── 정산용 집계 (분) ─────────────────────────────────────────────────────────

    /** 정규 근무 시간 (분) — 소정 근로 이내, 평일 (×1.0) */
    @Builder.Default
    @Column(name = "regular_minutes", nullable = false)
    private int regularMinutes = 0;

    /** 연장 근무 시간 (분) — 소정 근로 초과, 평일 (×1.5) */
    @Builder.Default
    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes = 0;

    /** 야간 근무 시간 (분) — 22:00~06:00 구간 (×0.5 가산) */
    @Builder.Default
    @Column(name = "night_minutes", nullable = false)
    private int nightMinutes = 0;

    /** 휴일 근무 시간 (분) — 소정 근로 이내, 소정 휴일 (×1.5) */
    @Builder.Default
    @Column(name = "holiday_minutes", nullable = false)
    private int holidayMinutes = 0;

    /** 휴일 연장 근무 시간 (분) — 소정 근로 초과, 소정 휴일 (×2.0) */
    @Builder.Default
    @Column(name = "holiday_overtime_minutes", nullable = false)
    private int holidayOvertimeMinutes = 0;
}
