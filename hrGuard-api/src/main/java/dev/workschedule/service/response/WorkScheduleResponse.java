package dev.workschedule.service.response;

import dev.workschedule.entity.WorkSchedule;

import java.time.LocalTime;

public record WorkScheduleResponse(
        Long memberId,
        String workDays,
        LocalTime startTime,
        LocalTime endTime,
        double dailyWorkHours,
        int hourlyWage
) {
    public static WorkScheduleResponse from(WorkSchedule workSchedule) {
        return new WorkScheduleResponse(
                workSchedule.getMemberId(),
                workSchedule.getWorkDays(),
                workSchedule.getStartTime(),
                workSchedule.getEndTime(),
                workSchedule.getDailyWorkHours(),
                workSchedule.getHourlyWage()
        );
    }
}
