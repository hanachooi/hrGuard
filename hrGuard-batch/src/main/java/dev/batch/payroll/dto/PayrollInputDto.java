package dev.batch.payroll.dto;

import dev.payrollpolicy.repository.projection.PayrollPolicyProjection;
import dev.workrecord.repository.projection.WorkRecordProjection;
import dev.workschedule.repository.projection.WorkScheduleProjection;

import java.util.List;

/**
 * Reader → Processor 전달용 DTO.
 * 도메인 projection 들을 조합한 read-only 입력 단위.
 * 영속성 컨텍스트에 엔티티가 들어가지 않아 dirty check 대상에서 제외된다.
 */
public record PayrollInputDto(
        Long memberId,
        WorkScheduleProjection workSchedule,
        PayrollPolicyProjection payrollPolicy,
        List<WorkRecordProjection> workRecords
) {
}
