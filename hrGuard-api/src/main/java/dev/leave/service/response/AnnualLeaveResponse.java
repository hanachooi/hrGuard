package dev.attendance.leave.service.response;

import dev.leave.constant.LeaveStatus;
import dev.leave.constant.LeaveType;
import dev.leave.entity.Leave;

import java.time.LocalDate;

public record AnnualLeaveResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        LeaveType leaveType,
        String reason,
        LeaveStatus status,
        String rejectReason,
        double deductionDays
) {
    public static AnnualLeaveResponse from(Leave leave) {
        return new AnnualLeaveResponse(
                leave.getId(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getLeaveType(),
                leave.getReason(),
                leave.getStatus(),
                leave.getRejectReason(),
                leave.getDeductionDays()
        );
    }
}
