package dev.commute.repository;

import dev.commute.constant.CommuteStatus;
import dev.commute.entity.Commute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommuteRepository extends JpaRepository<Commute, Long> {

    // 오늘 열린 세션(CHECKIN 상태) 존재 여부 — 중복 출근 방지
    boolean existsByMemberIdAndWorkDateAndStatus(Long memberId, LocalDate workDate, CommuteStatus status);

    // 오늘 열린 세션(CHECKIN 상태) 조회 — 퇴근 처리용
    Optional<Commute> findByMemberIdAndWorkDateAndStatus(Long memberId, LocalDate workDate, CommuteStatus status);

    // 특정 날짜의 모든 세션 조회 (출근 시각 오름차순) — 배치 Processor용
    List<Commute> findByMemberIdAndWorkDateOrderByInTimeAsc(Long memberId, LocalDate workDate);

    // 특정 기간의 출퇴근 기록 조회 — 급여 배치 보조 (하루 N건 반환 가능)
    List<Commute> findByMemberIdAndWorkDateBetween(Long memberId, LocalDate startDate, LocalDate endDate);

    // 해당 월에 출근 기록이 있는 memberId 목록 — 급여 배치 Reader용
    @Query("SELECT DISTINCT c.memberId FROM Commute c WHERE c.workDate BETWEEN :startDate AND :endDate")
    List<Long> findDistinctMemberIdsByYearMonth(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    // 특정 날짜에 완료된(CHECKOUT) 세션이 하나라도 있는 memberId 목록 — WorkRecordSync 배치 Reader용
    @Query("SELECT DISTINCT c.memberId FROM Commute c WHERE c.workDate = :date AND c.status = 'CHECKOUT'")
    List<Long> findMemberIdsWithCompletedCommuteByDate(@Param("date") LocalDate date);
}
