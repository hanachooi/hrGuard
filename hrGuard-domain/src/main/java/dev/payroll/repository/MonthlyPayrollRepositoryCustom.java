package dev.payroll.repository;

import dev.payroll.entity.MonthlyPayroll;

import java.util.List;

public interface MonthlyPayrollRepositoryCustom {

    void batchInsert(List<MonthlyPayroll> payrolls);
}
