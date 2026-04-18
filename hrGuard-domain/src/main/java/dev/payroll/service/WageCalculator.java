package dev.payroll.service;

import dev.payroll.constant.PayrollItemType;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import dev.workrecord.entity.WorkRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkRecord(일별 집계) + hourlyWage → PayrollItem 목록 생성.
 *
 * <p>WorkRecord의 정산용 집계 필드(분 단위)를 시간으로 변환 후 가산율 적용.</p>
 *
 * <h3>근로기준법 가산율 (제56조)</h3>
 * <pre>
 *   정규근로   : 통상임금 × 1.0
 *   연장근로   : 통상임금 × 0.5 (가산분만, 기본분은 정규에 포함)
 *   야간근로   : 통상임금 × 0.5 (다른 수당과 중복 적용)
 *   휴일근로   : 통상임금 × 1.5 (8h 이내)
 *   휴일연장   : 통상임금 × 2.0 (8h 초과)
 * </pre>
 */
@Component
public class WageCalculator {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal RATE_OVERTIME = new BigDecimal("1.5");
    private static final BigDecimal RATE_NIGHT = new BigDecimal("0.5");
    private static final BigDecimal RATE_HOLIDAY = new BigDecimal("1.5");
    private static final BigDecimal RATE_HOLIDAY_OVERTIME = new BigDecimal("2.0");

    private static BigDecimal minutesToHours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 10, RoundingMode.HALF_UP);
    }

    public List<PayrollItem> calculate(WorkRecord record, int hourlyWage, MonthlyPayroll payroll) {
        List<PayrollItem> items = new ArrayList<>();
        BigDecimal wage = BigDecimal.valueOf(hourlyWage);

        BigDecimal regularHours = minutesToHours(record.getRegularMinutes());
        BigDecimal overtimeHours = minutesToHours(record.getOvertimeMinutes());
        BigDecimal nightHours = minutesToHours(record.getNightMinutes());
        BigDecimal holidayHours = minutesToHours(record.getHolidayMinutes());
        BigDecimal holidayOvertimeHours = minutesToHours(record.getHolidayOvertimeMinutes());

        if (regularHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = regularHours.multiply(wage).setScale(0, RoundingMode.HALF_UP);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.BASIC_WAGE).hours(regularHours.doubleValue()).amount(amount).build());
        }

        if (overtimeHours.compareTo(BigDecimal.ZERO) > 0) {
            // 연장: 기본분(1.0)은 regularHours에 포함됐으므로 가산분(0.5)만 추가
            BigDecimal amount = overtimeHours.multiply(wage).multiply(RATE_OVERTIME).setScale(0, RoundingMode.HALF_UP);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.OVERTIME).hours(overtimeHours.doubleValue()).amount(amount).build());
        }

        if (nightHours.compareTo(BigDecimal.ZERO) > 0) {
            // 야간: 다른 수당과 중복 적용되는 가산분(0.5)만
            BigDecimal amount = nightHours.multiply(wage).multiply(RATE_NIGHT).setScale(0, RoundingMode.HALF_UP);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.NIGHT_WORK).hours(nightHours.doubleValue()).amount(amount).build());
        }

        if (holidayHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = holidayHours.multiply(wage).multiply(RATE_HOLIDAY).setScale(0, RoundingMode.HALF_UP);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.HOLIDAY_WORK).hours(holidayHours.doubleValue()).amount(amount).build());
        }

        if (holidayOvertimeHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = holidayOvertimeHours.multiply(wage).multiply(RATE_HOLIDAY_OVERTIME).setScale(0, RoundingMode.HALF_UP);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.HOLIDAY_OVERTIME).hours(holidayOvertimeHours.doubleValue()).amount(amount).build());
        }

        return items;
    }
}
