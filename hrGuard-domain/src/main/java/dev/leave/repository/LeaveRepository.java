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
     * 특정 날짜를 포함하는 승인된 휴가 목록 — 배치 Processor용.
     */
    @Query("""
            SELECT l FROM Leave l
            WHERE l.memberId = :memberId
              AND l.status = 'APPROVED'
              AND l.startDateTime < :nextDay
              AND l.endDateTime >= :startOfDay
            """)
    List<Leave> findApprovedByMemberIdAndDate(
            @Param("memberId") Long memberId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay);

    /**
     * 특정 날짜에 승인된 휴가가 있는 memberId 목록 — 배치 Reader용.
     */
    @Query("""
            SELECT DISTINCT l.memberId FROM Leave l
            WHERE l.status = 'APPROVED'
              AND l.startDateTime < :nextDay
              AND l.endDateTime >= :startOfDay
            """)
    List<Long> findApprovedMemberIdsByDate(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay);

    /**
     * 특정 기간에 승인된 휴가가 있는 memberId 목록 — (기존, 배치 보조 쿼리).
     */
    @Query("""
            SELECT DISTINCT l.memberId FROM Leave l
            WHERE l.status = 'APPROVED'
              AND l.startDateTime < :endDateTime
              AND l.endDateTime >= :startDateTime
            """)
    List<Long> findDistinctMemberIdsByApprovedLeaveInRange(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);
}
