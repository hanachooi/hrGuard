package dev.payroll.constant;

/**
 * 근무 유형.
 *
 * <p>근무 기록(WorkRecord)의 발생 원인을 구분합니다.
 * 급여 계산 로직은 WorkType에 무관하게 동일하게 적용되며,
 * 향후 유형별 특별 수당(출장비, 교통비 등) 확장 시 분기 기준으로 사용됩니다.</p>
 */
public enum WorkType {
    OFFICE,         // 사무실 근무 (출입 시스템 기반)
    FIELD,          // 외근 (현장 방문 등)
    REMOTE,         // 재택근무
    BUSINESS_TRIP,  // 출장 (사내 출장 신청 승인 기반)
    ANNUAL_LEAVE    // 휴가 (연차/반차/병가/공가 승인 기반) — 배치에서 휴게시간 차감 없이 정규시간 그대로 지급
}
