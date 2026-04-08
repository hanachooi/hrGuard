package dev.attendance.leave.service.response;

import dev.attendance.leave.entity.AnnualLeaveBalance;

public record AnnualLeaveBalanceResponse(
        Long memberId,
        int year,
        double totalDays,
        double usedDays,
        double remainingDays
) {
    public static AnnualLeaveBalanceResponse from(AnnualLeaveBalance balance) {
        return new AnnualLeaveBalanceResponse(
                balance.getMemberId(),
                balance.getYear(),
                balance.getTotalDays(),
                balance.getUsedDays(),
                balance.getRemainingDays()
        );
    }
}
