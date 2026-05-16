package dev.batch.payroll.dto;

import dev.payrollpolicy.repository.projection.PayrollPolicyProjection;
import dev.workrecord.repository.projection.WorkRecordProjection;
import dev.workschedule.repository.projection.WorkScheduleProjection;

import java.util.List;

/**
 * Reader → Processor 전달용 DTO.
 *
 * <p>도메인 projection 들을 조합한 read-only 입력 단위.
 * 영속성 컨텍스트에 엔티티가 들어가지 않아 dirty check 대상에서 제외된다.</p>
 *
 * <p>year/month 는 jobParameter 의 yearMonth 를 분해해 넣는다.
 * PROCESS 단계에서 skip 발생 시 DLT 적재의 시간 컨텍스트로 사용된다.</p>
 */
public record PayrollInputDto(
        Long memberId,
        int year,
        int month,
        WorkScheduleProjection workSchedule,
        PayrollPolicyProjection payrollPolicy,
        List<WorkRecordProjection> workRecords
) {
}
