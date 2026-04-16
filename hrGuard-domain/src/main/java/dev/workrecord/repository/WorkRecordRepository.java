package dev.workrecord.repository;

import dev.workrecord.entity.WorkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

    // 배치 Processor: idempotency — 기존 집계 레코드 삭제 후 재생성
    Optional<WorkRecord> findByMemberIdAndBizDate(Long memberId, LocalDate bizDate);

    // 배치 Processor (idempotency): 기존 레코드 삭제
    @Modifying
    @Query("DELETE FROM WorkRecord w WHERE w.memberId = :memberId AND w.bizDate = :bizDate")
    void deleteByMemberIdAndBizDate(
            @Param("memberId") Long memberId,
            @Param("bizDate") LocalDate bizDate);

    // 급여 배치 Processor: 특정 직원의 해당 월 근무 기록 전체 조회
    List<WorkRecord> findByMemberIdAndBizDateBetween(Long memberId, LocalDate startDate, LocalDate endDate);

    // 급여 배치 Reader: 해당 월에 근무 기록이 있는 memberId 목록
    @Query("SELECT DISTINCT w.memberId FROM WorkRecord w WHERE w.bizDate BETWEEN :startDate AND :endDate")
    List<Long> findDistinctMemberIdsByBizDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
