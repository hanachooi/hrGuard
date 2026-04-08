package dev.payroll.service;

import dev.payroll.constant.PayrollItemType;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// WorkTimeResult + hourlyWage → PayrollItem 목록 생성
// 근로기준법 가산율 적용:
//   연장근로: 통상임금 × 0.5 (가산분만, 기본분은 regularHours에 포함)
//   야간근로: 통상임금 × 0.5 (가산분만, 다른 수당과 중복 적용)
//   휴일근로 8h 이내: 통상임금 × 1.5
//   휴일근로 8h 초과: 통상임금 × 2.0
@Component
public class WageCalculator {

    public List<PayrollItem> calculate(WorkTimeResult result, int hourlyWage, MonthlyPayroll payroll) {
        List<PayrollItem> items = new ArrayList<>();
        double wage = hourlyWage;

        if (result.getRegularHours() > 0) {
            long amount = Math.round(result.getRegularHours() * wage);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.BASIC_WAGE).hours(result.getRegularHours()).amount(amount).build());
        }

        if (result.getOvertimeHours() > 0) {
            // 연장: 기본분(1.0)은 regularHours에 포함됐으므로 가산분(0.5)만 추가
            long amount = Math.round(result.getOvertimeHours() * wage * 1.5);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.OVERTIME).hours(result.getOvertimeHours()).amount(amount).build());
        }

        if (result.getNightHours() > 0) {
            // 야간: 다른 수당과 중복 적용되는 가산분(0.5)만
            long amount = Math.round(result.getNightHours() * wage * 0.5);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.NIGHT_WORK).hours(result.getNightHours()).amount(amount).build());
        }

        if (result.getHolidayHours() > 0) {
            long amount = Math.round(result.getHolidayHours() * wage * 1.5);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.HOLIDAY_WORK).hours(result.getHolidayHours()).amount(amount).build());
        }

        if (result.getHolidayOvertimeHours() > 0) {
            long amount = Math.round(result.getHolidayOvertimeHours() * wage * 2.0);
            items.add(PayrollItem.builder().monthlyPayroll(payroll).itemType(PayrollItemType.HOLIDAY_OVERTIME).hours(result.getHolidayOvertimeHours()).amount(amount).build());
        }

        return items;
    }
}
