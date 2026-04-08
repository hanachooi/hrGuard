package dev.workrecord.repository;

import dev.workrecord.constant.WorkType;
import dev.workrecord.entity.WorkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

    // 배치 Processor: 특정 사원의 월별 근무 기록 전체 조회 (하루 다건 포함)
    List<WorkRecord> findByMemberIdAndBizDateBetweenOrderByBizDateAscStartTimeAsc(
            Long memberId, LocalDate startDate, LocalDate endDate);

    // 배치 Reader: 해당 월에 근무 기록이 있는 memberId 목록
    @Query("SELECT DISTINCT w.memberId FROM WorkRecord w WHERE w.bizDate BETWEEN :startDate AND :endDate")
    List<Long> findDistinctMemberIdsByBizDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // WorkRecordSync 배치: 특정 사원의 특정 날짜 근무 기록 조회 (시작시간 오름차순)
    List<WorkRecord> findByMemberIdAndBizDateOrderByStartTimeAsc(Long memberId, LocalDate bizDate);

    // WorkRecordSync 배치 idempotency: 재실행 시 기존 OFFICE 레코드 삭제 후 재생성
    // 승인 기반 레코드(FIELD, BUSINESS_TRIP, ANNUAL_LEAVE 등)는 건드리지 않음
    @Modifying
    @Query("DELETE FROM WorkRecord w WHERE w.memberId = :memberId AND w.bizDate = :bizDate AND w.workType = :workType")
    void deleteByMemberIdAndBizDateAndWorkType(
            @Param("memberId") Long memberId,
            @Param("bizDate") LocalDate bizDate,
            @Param("workType") WorkType workType);
}
