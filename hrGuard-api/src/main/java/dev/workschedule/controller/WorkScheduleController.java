package dev.workschedule.controller;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.workschedule.service.WorkScheduleService;
import dev.workschedule.service.request.WorkScheduleUpsertRequest;
import dev.workschedule.service.response.WorkScheduleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/work-schedules")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    @GetMapping
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> getMySchedule(
            @AuthMemberId Long memberId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                WorkScheduleResponse.from(workScheduleService.findByMemberId(memberId))
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> createMySchedule(
            @AuthMemberId Long memberId,
            @Valid @RequestBody WorkScheduleUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                WorkScheduleResponse.from(workScheduleService.insert(
                        memberId,
                        request.workDays(),
                        request.startTime(),
                        request.endTime(),
                        request.dailyWorkHours(),
                        request.hourlyWage()
                ))
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> upsertMySchedule(
            @AuthMemberId Long memberId,
            @Valid @RequestBody WorkScheduleUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                WorkScheduleResponse.from(workScheduleService.update(
                        memberId,
                        request.workDays(),
                        request.startTime(),
                        request.endTime(),
                        request.dailyWorkHours(),
                        request.hourlyWage()
                ))
        ));
    }
}
