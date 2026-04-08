package dev.leave.constant;

public enum LeaveStatus {
    PENDING,   // 신청 대기
    APPROVED,  // 승인 → WorkRecord 자동 생성
    REJECTED   // 반려
}
