package dev.payroll.repository;

import dev.payroll.entity.SimplifiedTax;
import dev.payroll.repository.projection.SimplifiedTaxProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SimplifiedTaxRepository extends JpaRepository<SimplifiedTax, Long> {

    // 기준일에 유효한 간이세액표 전체를 projection 으로 1회 로드.
    // Job 시작 시 메모리에 적재되어 멤버별 N+1 쿼리를 제거한다.
    @Query("""
            SELECT new dev.payroll.repository.projection.SimplifiedTaxProjection(
                t.salaryMin, t.salaryMax,
                t.dep1, t.dep2, t.dep3, t.dep4, t.dep5, t.dep6,
                t.dep7, t.dep8, t.dep9, t.dep10, t.dep11
            )
            FROM SimplifiedTax t
            WHERE t.effectiveFrom <= :date
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :date)
            ORDER BY t.salaryMin ASC
            """)
    List<SimplifiedTaxProjection> findAllByEffectiveDate(@Param("date") LocalDate date);
}
