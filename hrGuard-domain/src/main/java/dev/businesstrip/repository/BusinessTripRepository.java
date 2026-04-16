package dev.businesstrip.repository;

import dev.businesstrip.entity.BusinessTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BusinessTripRepository extends JpaRepository<BusinessTrip, Long> {

    List<BusinessTrip> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("SELECT b FROM BusinessTrip b WHERE b.status = 'PENDING' ORDER BY b.createdAt DESC")
    List<BusinessTrip> findPending();

    /**
     * 특정 날짜를 포함하는 승인된 출장 목록 — 배치 Processor용.
     */
    @Query("""
            SELECT b FROM BusinessTrip b
            WHERE b.memberId = :memberId
              AND b.status = 'APPROVED'
              AND b.startDateTime < :nextDay
              AND b.endDateTime >= :startOfDay
            """)
    List<BusinessTrip> findApprovedByMemberIdAndDate(
            @Param("memberId") Long memberId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay);

    /**
     * 특정 날짜에 승인된 출장이 있는 memberId 목록 — 배치 Reader용.
     */
    @Query("""
            SELECT DISTINCT b.memberId FROM BusinessTrip b
            WHERE b.status = 'APPROVED'
              AND b.startDateTime < :nextDay
              AND b.endDateTime >= :startOfDay
            """)
    List<Long> findApprovedMemberIdsByDate(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay);
}
