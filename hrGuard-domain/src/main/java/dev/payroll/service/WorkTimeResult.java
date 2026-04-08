package dev.payroll.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 출퇴근 1건의 시간 구간 분석 결과
// TimeSegmentSplitter → WageCalculator 로 전달되는 DTO
@Getter
@RequiredArgsConstructor
public class WorkTimeResult {

    // 정규 근무 시간 (계약 시간 이내, 비공휴일)
    private final double regularHours;

    // 연장 근무 시간 (계약 시간 초과, 비공휴일, +50%)
    private final double overtimeHours;

    // 야간 근무 시간 (22:00~06:00 구간, +50%)
    private final double nightHours;

    // 휴일 근무 시간 (공휴일, 계약 시간 이내, +50%)
    private final double holidayHours;

    // 휴일 연장 시간 (공휴일, 계약 시간 초과, +100%)
    private final double holidayOvertimeHours;
}
