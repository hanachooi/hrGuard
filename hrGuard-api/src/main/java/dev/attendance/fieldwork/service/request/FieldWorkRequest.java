package dev.attendance.fieldwork.service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FieldWorkRequest(

        @NotNull(message = "외근 날짜는 필수입니다.")
        LocalDate workDate,

        @NotNull(message = "외근 시작 시각은 필수입니다.")
        LocalDateTime startTime,

        @NotNull(message = "외근 종료 시각은 필수입니다.")
        LocalDateTime endTime,

        @NotBlank(message = "외근 장소는 필수입니다.")
        @Size(max = 200)
        String location,

        @NotBlank(message = "외근 목적은 필수입니다.")
        @Size(max = 500)
        String purpose
) {
}
