package dev.payroll.repository;

import dev.payroll.constant.InsuranceType;
import dev.payroll.entity.InsuranceRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface InsuranceRateRepository extends JpaRepository<InsuranceRate, Long> {

    // 해당 날짜 기준 가장 최근에 발효된 요율 조회
    Optional<InsuranceRate> findFirstByTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            InsuranceType type, LocalDate date);
}
