package dev.workschedule.repository;

import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.repository.projection.WorkScheduleProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    Optional<WorkSchedule> findByMemberId(Long memberId);

    // 급여 배치 reader: 영속성 우회용 projection 조회 (단건)
    @Query("SELECT new dev.workschedule.repository.projection.WorkScheduleProjection(w.memberId, w.hourlyWage) " +
            "FROM WorkSchedule w WHERE w.memberId = :memberId")
    Optional<WorkScheduleProjection> findProjectionByMemberId(@Param("memberId") Long memberId);

    // 급여 배치 reader: IN절 bulk 조회 (chunk당 1회)
    @Query("SELECT new dev.workschedule.repository.projection.WorkScheduleProjection(w.memberId, w.hourlyWage) " +
            "FROM WorkSchedule w WHERE w.memberId IN :memberIds")
    List<WorkScheduleProjection> findProjectionByMemberIdIn(@Param("memberIds") List<Long> memberIds);
}
