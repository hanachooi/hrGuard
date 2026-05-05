package dev.workrecord.repository.projection;

import java.time.LocalDate;

/**
 * WorkRecord 의 정산용 projection.
 * JPQL 생성자 표현식으로 직접 매핑되어 영속성 컨텍스트를 거치지 않는다.
 * memberId 는 IN 절 결과를 사원 단위로 그루핑하기 위해 포함한다.
 */
public record WorkRecordProjection(
        Long memberId,
        LocalDate bizDate,
        int regularMinutes,
        int overtimeMinutes,
        int nightMinutes,
        int holidayMinutes,
        int holidayOvertimeMinutes
) {
}
