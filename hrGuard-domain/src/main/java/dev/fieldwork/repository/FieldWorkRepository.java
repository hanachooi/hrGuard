package dev.fieldwork.repository;

import dev.fieldwork.entity.FieldWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FieldWorkRepository extends JpaRepository<FieldWork, Long> {

    List<FieldWork> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("SELECT f FROM FieldWork f WHERE f.status = 'PENDING' ORDER BY f.createdAt DESC")
    List<FieldWork> findPending();

    /**
     * 특정 날짜를 포함하는 승인된 외근 목록 — 배치 Processor용.
     *
     * <p>시작일·중간일·종료일 모두 포함하기 위해 범위 겹침(range overlap) 조건을 사용합니다.</p>
     */
    @Query("""
            SELECT f FROM FieldWork f
            WHERE f.memberId = :memberId
              AND f.status = 'APPROVED'
              AND f.startDateTime < :nextDay
              AND f.endDateTime >= :startOfDay
            """)
    List<FieldWork> findApprovedByMemberIdAndWorkDate(
            @Param("memberId") Long memberId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay);

    /**
     * 특정 날짜에 승인된 외근이 있는 memberId 목록 — 배치 Reader용.
     *
     * <p>시작일·중간일·종료일 모두 포함하기 위해 범위 겹침(range overlap) 조건을 사용합니다.</p>
     */
    @Query("""
            SELECT DISTINCT f.memberId FROM FieldWork f
            WHERE f.status = 'APPROVED'
              AND f.startDateTime < :nextDay
              AND f.endDateTime >= :startOfDay
            """)
    List<Long> findApprovedMemberIdsByWorkDate(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay);
}
