package dev.leave.constant;

/**
 * 휴가 유형.
 *
 * <ul>
 *   <li>{@link #ANNUAL}  — 연차 (1일 단위, 연차 잔여일수 차감)</li>
 *   <li>{@link #HALF_AM} — 오전 반차 (0.5일, 09:00~13:00, 연차 잔여일수 차감)</li>
 *   <li>{@link #HALF_PM} — 오후 반차 (0.5일, 13:00~17:00, 연차 잔여일수 차감)</li>
 *   <li>{@link #SICK}    — 병가 (연차 차감 없음, 별도 관리)</li>
 *   <li>{@link #PUBLIC}  — 공가 (군입대, 검진 등, 연차 차감 없음)</li>
 * </ul>
 */
public enum LeaveType {

    ANNUAL,
    HALF_AM,
    HALF_PM,
    SICK,
    PUBLIC;

    /**
     * 연차 잔여일수를 차감하는 유형인지 여부.
     * SICK, PUBLIC은 별도 처리이므로 차감하지 않습니다.
     */
    public boolean deductsBalance() {
        return this == ANNUAL || this == HALF_AM || this == HALF_PM;
    }

    /**
     * 신청 1건당 차감 일수.
     * HALF_AM / HALF_PM은 0.5일, 나머지는 1일 단위(다일 신청 시 일수 × 1.0).
     */
    public double daysPerUnit() {
        return (this == HALF_AM || this == HALF_PM) ? 0.5 : 1.0;
    }
}
