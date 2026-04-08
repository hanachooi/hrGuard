package dev.fieldwork.constant;

public enum FieldWorkStatus {
    PENDING,   // 신청 대기
    APPROVED,  // 승인 → WorkRecord 자동 생성
    REJECTED   // 반려
}
