package dev.workschedule.repository;

import dev.workschedule.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    Optional<WorkSchedule> findByMemberId(Long memberId);
}
