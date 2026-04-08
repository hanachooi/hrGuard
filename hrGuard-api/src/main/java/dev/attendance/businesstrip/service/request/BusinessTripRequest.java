package dev.attendance.businesstrip.service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BusinessTripRequest(

        @NotNull(message = "출장 시작일은 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "출장 종료일은 필수입니다.")
        LocalDate endDate,

        @NotBlank(message = "출장지는 필수입니다.")
        @Size(max = 200)
        String destination,

        @NotBlank(message = "출장 목적은 필수입니다.")
        @Size(max = 500)
        String purpose
) {
}
