package dev.payroll.repository;

import dev.payroll.entity.SimplifiedTax;
import dev.payroll.repository.projection.SimplifiedTaxProjection;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SimplifiedTaxRepository extends JpaRepository<SimplifiedTax, Long> {

    // salary 단위: 천원 (salaryMin/salaryMax 컬럼과 동일 단위로 전달)
    // projection DTO + flushMode COMMIT: PC auto-flush dirty check 우회
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query("""
            SELECT new dev.payroll.repository.projection.SimplifiedTaxProjection(
                t.dep1, t.dep2, t.dep3, t.dep4, t.dep5, t.dep6,
                t.dep7, t.dep8, t.dep9, t.dep10, t.dep11
            )
            FROM SimplifiedTax t
            WHERE t.salaryMin <= :salary
              AND t.salaryMax >  :salary
              AND t.effectiveFrom <= :date
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :date)
            """)
    Optional<SimplifiedTaxProjection> findTax(
            @Param("salary") int salary,
            @Param("date") LocalDate date);
}
