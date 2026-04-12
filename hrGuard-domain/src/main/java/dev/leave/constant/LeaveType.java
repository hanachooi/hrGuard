package dev.leave.constant;

/**
 * 휴가 유형.
 *
 * <ul>
 *   <li>{@link #ANNUAL} — 연차 (잔여일수 차감)</li>
 *   <li>{@link #SICK}   — 병가 (연차 차감 없음)</li>
 *   <li>{@link #PUBLIC} — 공가 (연차 차감 없음)</li>
 * </ul>
 *
 * <p>반차·반반차는 유형 구분 없이 startDateTime~endDateTime 시간 범위로 표현합니다.</p>
 */
public enum LeaveType {

    ANNUAL,
    SICK,
    PUBLIC;

    /**
     * 연차 잔여일수를 차감하는 유형 여부.
     */
    public boolean deductsBalance() {
        return this == ANNUAL;
    }
}
