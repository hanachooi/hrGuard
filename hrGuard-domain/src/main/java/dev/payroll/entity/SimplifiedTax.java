package dev.payroll.entity;

import dev.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

// 근로소득 간이세액표 (국세청 별표2, 소득세법 시행령 제189조)
// 급여 단위: 천원 (CSV 원본과 동일, 예: 1250 = 1,250,000원)
// 세액 단위: 원 (dep1~dep11, null = 비과세)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "simplified_tax",
        indexes = @Index(columnList = "effective_from, salary_min")
)
public class SimplifiedTax extends BaseEntity {

    // 적용 시작일 (예: 2026-03-01)
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    // 적용 종료일 (null = 현재 유효)
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    // 월급여 구간 하한 (천원)
    @Column(name = "salary_min", nullable = false)
    private int salaryMin;

    // 월급여 구간 상한 (천원)
    @Column(name = "salary_max", nullable = false)
    private int salaryMax;

    // 공제대상 가족 수별 소득세 (원, null = 비과세)
    @Column(name = "dep_1")
    private Integer dep1;
    @Column(name = "dep_2")
    private Integer dep2;
    @Column(name = "dep_3")
    private Integer dep3;
    @Column(name = "dep_4")
    private Integer dep4;
    @Column(name = "dep_5")
    private Integer dep5;
    @Column(name = "dep_6")
    private Integer dep6;
    @Column(name = "dep_7")
    private Integer dep7;
    @Column(name = "dep_8")
    private Integer dep8;
    @Column(name = "dep_9")
    private Integer dep9;
    @Column(name = "dep_10")
    private Integer dep10;
    @Column(name = "dep_11")
    private Integer dep11;

    public int getTaxByDependents(int dependents) {
        Integer tax = switch (dependents) {
            case 1 -> dep1;
            case 2 -> dep2;
            case 3 -> dep3;
            case 4 -> dep4;
            case 5 -> dep5;
            case 6 -> dep6;
            case 7 -> dep7;
            case 8 -> dep8;
            case 9 -> dep9;
            case 10 -> dep10;
            default -> dep11;
        };
        return tax != null ? tax : 0;
    }
}
