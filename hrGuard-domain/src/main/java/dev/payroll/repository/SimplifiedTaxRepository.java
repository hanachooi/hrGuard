package dev.payroll.repository;

import dev.payroll.entity.SimplifiedTax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SimplifiedTaxRepository extends JpaRepository<SimplifiedTax, Long> {

    // salary 단위: 천원 (salaryMin/salaryMax 컬럼과 동일 단위로 전달)
    @Query("""
            SELECT t FROM SimplifiedTax t
            WHERE t.salaryMin <= :salary
              AND t.salaryMax >  :salary
              AND t.effectiveFrom <= :date
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :date)
            """)
    Optional<SimplifiedTax> findTax(
            @Param("salary") int salary,
            @Param("date") LocalDate date);
}
