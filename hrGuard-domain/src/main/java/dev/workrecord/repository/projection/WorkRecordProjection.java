package dev.workrecord.repository.projection;

import java.time.LocalDate;

/**
 * WorkRecord 의 정산용 projection.
 * JPQL 생성자 표현식으로 직접 매핑되어 영속성 컨텍스트를 거치지 않는다.
 */
public record WorkRecordProjection(
        LocalDate bizDate,
        int regularMinutes,
        int overtimeMinutes,
        int nightMinutes,
        int holidayMinutes,
        int holidayOvertimeMinutes
) {
}
