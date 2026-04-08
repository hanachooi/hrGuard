package dev.leave.repository;

import dev.leave.constant.LeaveStatus;
import dev.leave.entity.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByMemberIdOrderByStartDateDesc(Long memberId);

    List<Leave> findByStatusOrderByStartDateDesc(LeaveStatus status);

    /**
     * 해당 월에 승인된 휴가 날짜 Set을 반환합니다.
     * 배치 Processor에서 "퇴근 미기록" 여부를 판단할 때 사용합니다.
     */
    @Query("""
            SELECT a FROM Leave a
            WHERE a.memberId = :memberId
              AND a.status = 'APPROVED'
              AND a.startDate <= :endDate
              AND a.endDate   >= :startDate
            """)
    List<Leave> findApprovedByMemberIdAndDateRange(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 날짜에 승인된 휴가가 있는 memberId 목록.
     * 배치 Reader 보조 쿼리로 사용 가능합니다.
     */
    @Query("""
            SELECT DISTINCT a.memberId FROM Leave a
            WHERE a.status = 'APPROVED'
              AND a.startDate <= :endDate
              AND a.endDate   >= :startDate
            """)
    List<Long> findDistinctMemberIdsByApprovedLeaveInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
