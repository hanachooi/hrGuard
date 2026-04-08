package dev.attendance.leave.service.request;

import dev.attendance.leave.constant.LeaveType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AnnualLeaveRequest(

        @NotNull(message = "휴가 시작일은 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "휴가 종료일은 필수입니다.")
        LocalDate endDate,

        @NotNull(message = "휴가 유형은 필수입니다.")
        LeaveType leaveType,

        @Size(max = 500)
        String reason
) {
}
