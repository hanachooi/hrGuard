package dev.leave.service.response;

import dev.leave.entity.LeaveBalance;

public record AnnualLeaveBalanceResponse(
        Long memberId,
        int year,
        double totalDays,
        double usedDays,
        double remainingDays
) {
    public static AnnualLeaveBalanceResponse from(LeaveBalance balance) {
        return new AnnualLeaveBalanceResponse(
                balance.getMemberId(),
                balance.getYear(),
                balance.getTotalDays(),
                balance.getUsedDays(),
                balance.getRemainingDays()
        );
    }
}
