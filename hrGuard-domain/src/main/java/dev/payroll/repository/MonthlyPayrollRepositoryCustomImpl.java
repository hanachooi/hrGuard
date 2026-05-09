package dev.payroll.repository;

import dev.payroll.entity.MonthlyPayroll;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MonthlyPayrollRepositoryCustomImpl implements MonthlyPayrollRepositoryCustom {

    private static final String INSERT_SQL = """
            INSERT INTO monthly_payroll (
                id, created_at, updated_at, member_id, `year`, `month`,
                total_amount, national_pension, health_insurance, long_term_care,
                employment_insurance, income_tax, local_income_tax,
                total_deduction, net_pay, overtime_limit_exceeded,
                max_weekly_overtime_hours, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchInsert(List<MonthlyPayroll> payrolls) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                payrolls,
                payrolls.size(),
                (ps, p) -> {
                    ps.setLong(1, p.getId());
                    ps.setTimestamp(2, now);
                    ps.setTimestamp(3, now);
                    ps.setLong(4, p.getMemberId());
                    ps.setInt(5, p.getYear());
                    ps.setInt(6, p.getMonth());
                    ps.setLong(7, p.getTotalAmount());
                    ps.setLong(8, p.getNationalPension());
                    ps.setLong(9, p.getHealthInsurance());
                    ps.setLong(10, p.getLongTermCare());
                    ps.setLong(11, p.getEmploymentInsurance());
                    ps.setLong(12, p.getIncomeTax());
                    ps.setLong(13, p.getLocalIncomeTax());
                    ps.setLong(14, p.getTotalDeduction());
                    ps.setLong(15, p.getNetPay());
                    ps.setBoolean(16, p.isOvertimeLimitExceeded());
                    ps.setDouble(17, p.getMaxWeeklyOvertimeHours());
                    ps.setString(18, p.getStatus().name());
                }
        );
    }
}
