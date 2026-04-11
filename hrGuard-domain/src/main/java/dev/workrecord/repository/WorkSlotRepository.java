package dev.workrecord.repository;

import dev.workrecord.entity.WorkSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * WorkSlot 레포지토리.
 *
 * <p>WorkSlot 의 생명주기는 WorkRecord 를 통해 관리됩니다
 * ({@code CascadeType.ALL + orphanRemoval = true}).
 * 직접 삭제·저장보다는 {@code WorkRecord.addSlot()} /
 * {@code WorkRecord.removeSlotsByType()} 을 사용하세요.</p>
 *
 * <p>이 레포지토리는 급여 배치나 통계 쿼리 등 WorkRecord 를 거치지 않고
 * 슬롯을 직접 조회해야 하는 경우를 위해 제공됩니다.</p>
 */
@Repository
public interface WorkSlotRepository extends JpaRepository<WorkSlot, Long> {
}
