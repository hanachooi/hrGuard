package dev.payroll.repository;

import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.repository.projection.MonthlyPayrollIdMember;
import dev.payroll.repository.projection.MonthlyPayrollProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyPayrollRepository extends JpaRepository<MonthlyPayroll, Long>, MonthlyPayrollRepositoryCustom {

    Optional<MonthlyPayroll> findByMemberIdAndYearAndMonth(Long memberId, int year, int month);

    @Query("SELECT new dev.payroll.repository.projection.MonthlyPayrollProjection(p.id) " +
            "FROM MonthlyPayroll p WHERE p.memberId = :memberId AND p.year = :year AND p.month = :month")
    Optional<MonthlyPayrollProjection> findProjectionByMemberIdAndYearAndMonth(
            @Param("memberId") Long memberId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("SELECT p.id FROM MonthlyPayroll p " +
            "WHERE p.year = :year AND p.month = :month AND p.memberId IN :memberIds")
    List<Long> findIdsByYearAndMonthAndMemberIdIn(
            @Param("year") int year,
            @Param("month") int month,
            @Param("memberIds") List<Long> memberIds);

    @Query("SELECT new dev.payroll.repository.projection.MonthlyPayrollIdMember(p.id, p.memberId) " +
            "FROM MonthlyPayroll p " +
            "WHERE p.year = :year AND p.month = :month AND p.memberId IN :memberIds")
    List<MonthlyPayrollIdMember> findIdAndMemberIdByYearAndMonthAndMemberIdIn(
            @Param("year") int year,
            @Param("month") int month,
            @Param("memberIds") List<Long> memberIds);

    @Modifying
    @Query("DELETE FROM MonthlyPayroll p WHERE p.id = :id")
    void deleteByPayrollId(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM MonthlyPayroll p WHERE p.id IN :ids")
    void deleteByIdIn(@Param("ids") List<Long> ids);

}
