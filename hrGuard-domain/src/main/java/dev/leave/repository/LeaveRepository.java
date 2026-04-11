package dev.leave.repository;

import dev.leave.constant.LeaveStatus;
import dev.leave.entity.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByMemberIdOrderByStartDateTimeDesc(Long memberId);

    List<Leave> findByStatusOrderByStartDateTimeDesc(LeaveStatus status);

    /**
     * 해당 기간과 겹치는 승인된 휴가 목록을 반환합니다.
     * 배치 Processor에서 "퇴근 미기록" 여부를 판단할 때 사용합니다.
     */
    @Query("""
            SELECT a FROM Leave a
            WHERE a.memberId = :memberId
              AND a.status = 'APPROVED'
              AND a.startDateTime < :endDateTime
              AND a.endDateTime   > :startDateTime
            """)
    List<Leave> findApprovedByMemberIdAndDateRange(
            @Param("memberId") Long memberId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * 특정 기간에 승인된 휴가가 있는 memberId 목록.
     * 배치 Reader 보조 쿼리로 사용 가능합니다.
     */
    @Query("""
            SELECT DISTINCT a.memberId FROM Leave a
            WHERE a.status = 'APPROVED'
              AND a.startDateTime < :endDateTime
              AND a.endDateTime   > :startDateTime
            """)
    List<Long> findDistinctMemberIdsByApprovedLeaveInRange(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}
