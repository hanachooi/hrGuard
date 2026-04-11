package dev.workrecord.entity;

import dev.common.BaseEntity;
import dev.workrecord.constant.WorkType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 직원 1인 1일 근무 기록 — 급여 계산의 최종 확정본.
 *
 * <p>{@code (member_id, biz_date)} UNIQUE 제약으로 하루에 반드시 1건만 존재합니다.
 * 실제 근무 시간 세그먼트는 {@link WorkSlot}에 1:N 으로 보관되며,
 * 급여 배치는 이 슬롯 목록을 합산하여 계산합니다.</p>
 *
 * <h3>슬롯 관리 정책</h3>
 * <pre>
 *   ┌─────────────────────────────────────────────────────────┐
 *   │ 승인 기반 (FIELD / REMOTE / BUSINESS_TRIP / ANNUAL_LEAVE) │
 *   │  → 관리자 승인 즉시 WorkRecordService.registerApprovedSlot() │
 *   │  → 이미 점유된 구간 신청 불가 (SLOT_CONFLICT 예외)         │
 *   ├─────────────────────────────────────────────────────────┤
 *   │ 로그 기반 (OFFICE)                                      │
 *   │  → 익일 배치(WorkRecordComputeProcessor)               │
 *   │  → 승인 기반 슬롯을 차집합으로 제외한 빈 구간만 채움       │
 *   │  → 매 실행 시 기존 OFFICE 슬롯 삭제 후 재산출 (멱등)      │
 *   └─────────────────────────────────────────────────────────┘
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

    /**
     * 근무 시간 세그먼트 목록.
     *
     * <p>PERSIST·MERGE cascade 와 orphanRemoval 로 슬롯 추가/삭제가 WorkRecord 를 통해서만
     * 이루어지도록 강제합니다. 직접 WorkSlotRepository 를 조작하지 마세요.</p>
     *
     * <p>{@code insertable = false, updatable = false}: FK(work_record_id) 는
     * WorkSlot 생성 시점에 직접 주입되므로 JPA 가 별도 UPDATE 를 수행하지 않습니다.</p>
     */
    @Builder.Default
    @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    @JoinColumn(name = "work_record_id", nullable = false, insertable = false, updatable = false)
    private List<WorkSlot> slots = new ArrayList<>();

    // ── 팩토리 ──────────────────────────────────────────────────────────────────

    /**
     * 특정 직원·날짜의 하루 마스터 레코드를 생성합니다.
     *
     * @param memberId 대상 직원 ID
     * @param bizDate  논리 근무일
     */
    public static WorkRecord of(Long memberId, LocalDate bizDate) {
        return WorkRecord.builder()
                .memberId(memberId)
                .bizDate(bizDate)
                .build();
    }

    // ── 도메인 행위 ──────────────────────────────────────────────────────────────

    /**
     * 슬롯을 추가합니다.
     *
     * <p>슬롯 충돌 검증은 호출자({@code WorkRecordService})가 책임집니다.
     * 이 메서드는 순수하게 컬렉션 조작만 수행합니다.</p>
     *
     * @param startTime 시작 시각
     * @param endTime   종료 시각
     * @param workType  근무 유형
     */
    public void addSlot(LocalDateTime startTime, LocalDateTime endTime, WorkType workType) {
        slots.add(WorkSlot.create(this.id, startTime, endTime, workType));
    }

    /**
     * 특정 근무 유형의 슬롯을 모두 제거합니다.
     *
     * <p>orphanRemoval 로 인해 제거된 WorkSlot 은 트랜잭션 커밋 시 DB 에서 삭제됩니다.</p>
     *
     * @param workType 제거할 근무 유형
     */
    public void removeSlotsByType(WorkType workType) {
        slots.removeIf(s -> s.getWorkType() == workType);
    }

    /**
     * 특정 근무 유형의 슬롯 목록을 반환합니다.
     */
    public List<WorkSlot> getSlotsByType(WorkType workType) {
        return slots.stream()
                .filter(s -> s.getWorkType() == workType)
                .toList();
    }

    /**
     * OFFICE 를 제외한 승인 기반 슬롯 목록을 시작 시각 오름차순으로 반환합니다.
     *
     * <p>WorkRecordComputeProcessor 가 차집합 계산 시 사용합니다.</p>
     */
    public List<WorkSlot> getBlockedSlots() {
        return slots.stream()
                .filter(s -> s.getWorkType() != WorkType.OFFICE)
                .filter(s -> s.getEndTime() != null)
                .sorted(Comparator.comparing(WorkSlot::getStartTime))
                .toList();
    }

    /**
     * 지정 구간 [{@code start}, {@code end}) 와 겹치는 슬롯이 있으면 {@code true}.
     *
     * <p>구간 겹침 조건: slotStart &lt; end &amp;&amp; slotEnd &gt; start</p>
     */
    public boolean hasConflict(LocalDateTime start, LocalDateTime end) {
        return slots.stream()
                .anyMatch(s -> s.getStartTime().isBefore(end) && s.getEndTime().isAfter(start));
    }
}
