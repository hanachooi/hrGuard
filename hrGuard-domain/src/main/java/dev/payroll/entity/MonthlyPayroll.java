package dev.payroll.entity;

import dev.common.BaseEntity;
import dev.payroll.constant.PayrollStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

// 월별 급여 집계 테이블 (배치가 매월 말 생성)
// status: DRAFT → CONFIRMED → PAID
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "year", "month"}))
public class MonthlyPayroll extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    // 해당 월 총 지급액 (원)
    @Builder.Default
    @Column(name = "total_amount", nullable = false)
    private long totalAmount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus status = PayrollStatus.DRAFT;

    // DB 컬럼 아님 — 배치 processor → writer 간 items 전달용
    @Builder.Default
    @Transient
    private List<PayrollItem> pendingItems = new ArrayList<>();

    public void updateTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<PayrollItem> getPendingItems() {
        return pendingItems;
    }

    public void setPendingItems(List<PayrollItem> items) {
        this.pendingItems = items;
    }
}
