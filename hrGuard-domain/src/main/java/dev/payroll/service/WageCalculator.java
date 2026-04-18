package dev.payroll.service;

import dev.payroll.constant.PayrollItemType;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import dev.workrecord.entity.WorkRecord;
import org.springframework.stereotype.Component;

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

    public List<PayrollItem> calculate(WorkRecord record, int hourlyWage, MonthlyPayroll payroll) {
        List<PayrollItem> items = new ArrayList<>();
        double wage = hourlyWage;

        double regularHours = record.getRegularMinutes() / 60.0;
        double overtimeHours = record.getOvertimeMinutes() / 60.0;
        double nightHours = record.getNightMinutes() / 60.0;
        double holidayHours = record.getHolidayMinutes() / 60.0;
        double holidayOvertimeHours = record.getHolidayOvertimeMinutes() / 60.0;

        if (regularHours > 0) {
            long amount = Math.round(regularHours * wage);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.BASIC_WAGE).hours(regularHours).amount(amount).build());
        }

        if (overtimeHours > 0) {
            // 연장: 기본분(1.0)은 regularHours에 포함됐으므로 가산분(0.5)만 추가
            long amount = Math.round(overtimeHours * wage * 1.5);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.OVERTIME).hours(overtimeHours).amount(amount).build());
        }

        if (nightHours > 0) {
            // 야간: 다른 수당과 중복 적용되는 가산분(0.5)만
            long amount = Math.round(nightHours * wage * 0.5);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.NIGHT_WORK).hours(nightHours).amount(amount).build());
        }

        if (holidayHours > 0) {
            long amount = Math.round(holidayHours * wage * 1.5);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.HOLIDAY_WORK).hours(holidayHours).amount(amount).build());
        }

        if (holidayOvertimeHours > 0) {
            long amount = Math.round(holidayOvertimeHours * wage * 2.0);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.HOLIDAY_OVERTIME).hours(holidayOvertimeHours).amount(amount).build());
        }

        return items;
    }
}
