package dev.businesstrip.service.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 출장 신청 요청 DTO.
 */
public record BusinessTripRequest(

        @NotNull(message = "출장 시작 일시는 필수입니다.")
        LocalDateTime startDateTime,

        @NotNull(message = "출장 종료 일시는 필수입니다.")
        LocalDateTime endDateTime,

        @NotBlank(message = "출장지는 필수입니다.")
        @Size(max = 200)
        String destination,

        @NotBlank(message = "출장 목적은 필수입니다.")
        @Size(max = 500)
        String purpose
) {
    @AssertTrue(message = "출장 종료 일시는 시작 일시보다 늦어야 합니다.")
    public boolean isValidDateTimeRange() {
        if (startDateTime == null || endDateTime == null) return true;
        return endDateTime.isAfter(startDateTime);
    }
}
