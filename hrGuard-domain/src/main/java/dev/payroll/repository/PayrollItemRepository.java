package dev.payroll.repository;

import dev.payroll.entity.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long>, PayrollItemRepositoryCustom {

    @Modifying
    @Query("DELETE FROM PayrollItem p WHERE p.monthlyPayroll.id = :monthlyPayrollId")
    void deleteAllByMonthlyPayrollId(@Param("monthlyPayrollId") Long monthlyPayrollId);

    @Modifying
    @Query("DELETE FROM PayrollItem p WHERE p.monthlyPayroll.id IN :ids")
    void deleteByMonthlyPayrollIdIn(@Param("ids") List<Long> ids);

}
