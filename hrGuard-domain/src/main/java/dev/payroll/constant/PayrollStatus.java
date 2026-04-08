package dev.payroll.constant;

public enum PayrollStatus {
    DRAFT,      // 배치 계산 완료, 미확정
    CONFIRMED,  // 담당자 확정
    PAID        // 지급 완료
}
