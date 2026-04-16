package dev.workrecord.constant;

/**
 * 근무 유형 — WorkRecordComputeProcessor가 원천 데이터를 분류하는 기준.
 *
 * <p>WorkRecord 엔티티에는 더 이상 workType 필드가 없으며,
 * 각 유형별 분 단위 집계값(officeMinutes, leaveMinutes, ...)으로 저장됩니다.
 * 이 열거형은 배치 내부 로직 분기 및 관련 상수 정의 목적으로 유지합니다.</p>
 */
public enum WorkType {
    OFFICE,         // 사무실 근무 — Commute 기반, gap 보정 후 차집합으로 산출
    FIELD,          // 외근 — 승인된 FieldWork 기반
    REMOTE,         // 재택근무 — 승인된 RemoteWork 기반 (현재 미구현)
    BUSINESS_TRIP,  // 출장 — 승인된 BusinessTrip 기반, 소정 근무 시간 전체
    ANNUAL_LEAVE    // 휴가 — 승인된 Leave 기반, 휴게시간 미차감
}
