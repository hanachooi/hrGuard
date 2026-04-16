package dev.workschedule.service;

import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.exception.WorkScheduleError;
import dev.workschedule.exception.WorkScheduleException;
import dev.workschedule.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;

    @Transactional
    public WorkSchedule insert(Long memberId, String workDays, LocalTime startTime,
                               LocalTime endTime, double dailyWorkHours, int hourlyWage) {
        if (workScheduleRepository.findByMemberId(memberId).isPresent()) {
            throw new WorkScheduleException(WorkScheduleError.WORK_SCHEDULE_ALREADY_EXISTS);
        }
        return workScheduleRepository.save(
                WorkSchedule.create(memberId, workDays, startTime, endTime, dailyWorkHours, hourlyWage)
        );
    }

    @Transactional
    public WorkSchedule update(Long memberId, String workDays, LocalTime startTime,
                               LocalTime endTime, double dailyWorkHours, int hourlyWage) {
        WorkSchedule schedule = findByMemberId(memberId);
        schedule.update(workDays, startTime, endTime, dailyWorkHours, hourlyWage);
        return schedule;
    }

    @Transactional(readOnly = true)
    public WorkSchedule findByMemberId(Long memberId) {
        return workScheduleRepository.findByMemberId(memberId)
                .orElseThrow(() -> new WorkScheduleException(WorkScheduleError.WORK_SCHEDULE_NOT_FOUND));
    }
}
