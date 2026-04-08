package dev.businesstrip.constant;

public enum BusinessTripStatus {
    PENDING,   // 신청 대기
    APPROVED,  // 승인 → WorkRecord 자동 생성
    REJECTED   // 반려
}