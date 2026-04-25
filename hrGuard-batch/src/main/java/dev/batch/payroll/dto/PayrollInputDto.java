package dev.batch.payroll.dto;

import dev.payrollpolicy.entity.PayrollPolicy;
import dev.workrecord.entity.WorkRecord;
import dev.workschedule.entity.WorkSchedule;

import java.util.List;

/**
 * Reader → Processor 전달용 DTO.
 * DB 조회는 Reader에서 완료하고, Processor는 계산만 담당한다.
 */
public record PayrollInputDto(
        Long memberId,
        WorkSchedule workSchedule,
        PayrollPolicy payrollPolicy,
        List<WorkRecord> workRecords
) {
}
