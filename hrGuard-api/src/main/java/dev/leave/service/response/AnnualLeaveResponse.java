package dev.leave.service.response;

import dev.leave.constant.LeaveStatus;
import dev.leave.constant.LeaveType;
import dev.leave.entity.Leave;

import java.time.LocalDateTime;

public record AnnualLeaveResponse(
        Long id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        LeaveType leaveType,
        String reason,
        LeaveStatus status,
        String rejectReason,
        double deductionDays
) {
    public static AnnualLeaveResponse from(Leave leave) {
        return new AnnualLeaveResponse(
                leave.getId(),
                leave.getStartDateTime(),
                leave.getEndDateTime(),
                leave.getLeaveType(),
                leave.getReason(),
                leave.getStatus(),
                leave.getRejectReason(),
                leave.getDeductionDays()
        );
    }
}
