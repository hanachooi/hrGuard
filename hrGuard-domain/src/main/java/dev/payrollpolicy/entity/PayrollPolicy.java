package dev.payrollpolicy.entity;

import dev.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 멤버별 급여 정산 정책.
 *
 * <p>근무 일정(WorkSchedule)과 분리된 정산 전용 설정으로,
 * 근로소득세 원천징수 및 비과세 처리에 사용됩니다.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "payroll_policy",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id"}))
public class PayrollPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * 부양가족 수 (본인 포함, 최소 1).
     * 근로소득세 간이세액표 기본공제 산정에 사용.
     * 예) 본인만 → 1, 배우자+자녀1명 → 3
     */
    @Column(nullable = false)
    private int dependents;

    /**
     * 월 식대 비과세 금액 (원).
     * 소득세법 시행령 제12조 — 회사 식사 미제공 시 월 20만원 한도 비과세.
     * 회사가 식사를 현물 제공하는 경우 0으로 설정.
     */
    @Column(name = "non_taxable_meal_allowance", nullable = false)
    private long nonTaxableMealAllowance;

    public void update(int dependents, long nonTaxableMealAllowance) {
        this.dependents = dependents;
        this.nonTaxableMealAllowance = nonTaxableMealAllowance;
    }
}
