package dev.holiday.repository;

import dev.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    // 해당 월 공휴일 전체 조회 (배치에서 holiday Set 구성용)
    List<Holiday> findAllByDateBetween(LocalDate start, LocalDate end);

    boolean existsByDate(LocalDate date);
}
