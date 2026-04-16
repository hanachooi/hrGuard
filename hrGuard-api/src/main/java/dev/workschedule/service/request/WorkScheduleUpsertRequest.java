package dev.workschedule.service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

public record WorkScheduleUpsertRequest(
        @NotBlank(message = "근무 요일은 필수입니다.")
        String workDays,

        @NotNull(message = "근무 시작 시각은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "근무 종료 시각은 필수입니다.")
        LocalTime endTime,

        @Positive(message = "일 소정 근로시간은 0보다 커야 합니다.")
        double dailyWorkHours,

        @Positive(message = "시급은 0보다 커야 합니다.")
        int hourlyWage
) {
}
