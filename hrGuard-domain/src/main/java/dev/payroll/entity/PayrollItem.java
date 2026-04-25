package dev.payroll.entity;

import com.github.f4b6a3.tsid.TsidCreator;
import dev.payroll.constant.PayrollItemType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;

// 급여 상세 항목 (기본급, 연장수당, 야간수당, 휴일수당 등)
// MonthlyPayroll 1:N 구조로 정규화
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PayrollItem implements Persistable<Long> {

    @Id
    @Builder.Default
    private Long id = TsidCreator.getTsid().toLong();

    // Builder로 생성된 엔티티는 신규(true), DB에서 로드된 엔티티는 @PostLoad로 false 전환
    @Transient
    @Builder.Default
    private boolean isNew = true;

    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_payroll_id", nullable = false)
    private MonthlyPayroll monthlyPayroll;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private PayrollItemType itemType;

    // 해당 항목 근무 시간 (소수점 허용, e.g. 2.5시간)
    @Column(nullable = false)
    private double hours;

    // 해당 항목 금액 (원)
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;
}
