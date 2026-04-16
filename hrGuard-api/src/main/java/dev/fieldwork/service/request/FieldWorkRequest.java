package dev.fieldwork.service.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 외근 신청 요청 DTO.
 */
public record FieldWorkRequest(

        @NotNull(message = "외근 시작 일시는 필수입니다.")
        LocalDateTime startDateTime,

        @NotNull(message = "외근 종료 일시는 필수입니다.")
        LocalDateTime endDateTime,

        @NotBlank(message = "외근 장소는 필수입니다.")
        @Size(max = 200)
        String location,

        @NotBlank(message = "외근 목적은 필수입니다.")
        @Size(max = 500)
        String purpose
) {
    @AssertTrue(message = "외근 종료 일시는 시작 일시보다 늦어야 합니다.")
    public boolean isValidDateTimeRange() {
        if (startDateTime == null || endDateTime == null) return true;
        return endDateTime.isAfter(startDateTime);
    }
}
