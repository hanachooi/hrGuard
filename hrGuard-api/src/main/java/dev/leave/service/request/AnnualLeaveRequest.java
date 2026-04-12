package dev.leave.service.request;

import dev.leave.constant.LeaveType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 휴가 신청 요청 DTO.
 */
public record AnnualLeaveRequest(

        @NotNull(message = "휴가 시작 일시는 필수입니다.")
        LocalDateTime startDateTime,

        @NotNull(message = "휴가 종료 일시는 필수입니다.")
        LocalDateTime endDateTime,

        @NotNull(message = "휴가 유형은 필수입니다.")
        LeaveType leaveType,

        @Size(max = 500)
        String reason
) {
    @AssertTrue(message = "휴가 종료 일시는 시작 일시보다 늦어야 합니다.")
    public boolean isValidDateTimeRange() {
        if (startDateTime == null || endDateTime == null) return true;
        return endDateTime.isAfter(startDateTime);
    }
}
