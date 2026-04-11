package dev.workrecord.repository;

import dev.workrecord.entity.WorkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

    // 단건 조회 (WorkRecordService.getOrCreate, 충돌 검사 등)
    Optional<WorkRecord> findByMemberIdAndBizDate(Long memberId, LocalDate bizDate);

    // 급여 배치 Processor: 슬롯 포함 월별 조회 (N+1 방지 JOIN FETCH)
    @Query("""
            SELECT w FROM WorkRecord w
            LEFT JOIN FETCH w.slots
            WHERE w.memberId = :memberId
              AND w.bizDate BETWEEN :startDate AND :endDate
            ORDER BY w.bizDate ASC
            """)
    List<WorkRecord> findWithSlotsByMemberIdAndBizDateBetween(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 급여 배치 Reader: 해당 월에 근무 기록이 있는 memberId 목록
    @Query("SELECT DISTINCT w.memberId FROM WorkRecord w WHERE w.bizDate BETWEEN :startDate AND :endDate")
    List<Long> findDistinctMemberIdsByBizDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
