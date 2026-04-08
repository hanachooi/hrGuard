package dev.payroll.repository;

import dev.payroll.entity.MonthlyPayroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonthlyPayrollRepository extends JpaRepository<MonthlyPayroll, Long> {

    Optional<MonthlyPayroll> findByMemberIdAndYearAndMonth(Long memberId, int year, int month);
}
