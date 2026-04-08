package dev.payroll.constant;

public enum PayrollItemType {
    BASIC_WAGE,         // 기본급 (정규 근무)
    OVERTIME,           // 연장수당 (일 계약시간 초과, +50%)
    NIGHT_WORK,         // 야간수당 (22:00~06:00, +50%)
    HOLIDAY_WORK,       // 휴일수당 (공휴일 8시간 이내, +50%)
    HOLIDAY_OVERTIME    // 휴일연장수당 (공휴일 8시간 초과, +100%)
}
