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

    private static final String UPSERT_SQL = """
            INSERT INTO monthly_payroll (
                id, created_at, updated_at, member_id, `year`, `month`,
                total_amount, national_pension, health_insurance, long_term_care,
                employment_insurance, income_tax, local_income_tax,
                total_deduction, net_pay, overtime_limit_exceeded,
                max_weekly_overtime_hours, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                updated_at              = VALUES(updated_at),
                total_amount            = VALUES(total_amount),
                national_pension        = VALUES(national_pension),
                health_insurance        = VALUES(health_insurance),
                long_term_care          = VALUES(long_term_care),
                employment_insurance    = VALUES(employment_insurance),
                income_tax              = VALUES(income_tax),
                local_income_tax        = VALUES(local_income_tax),
                total_deduction         = VALUES(total_deduction),
                net_pay                 = VALUES(net_pay),
                overtime_limit_exceeded = VALUES(overtime_limit_exceeded),
                max_weekly_overtime_hours = VALUES(max_weekly_overtime_hours),
                status                  = VALUES(status)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchInsert(List<MonthlyPayroll> payrolls) {
        runBatch(INSERT_SQL, payrolls);
    }

    @Override
    public void bulkUpsert(List<MonthlyPayroll> payrolls) {
        runBatch(UPSERT_SQL, payrolls);
    }

    private void runBatch(String sql, List<MonthlyPayroll> payrolls) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.batchUpdate(
                sql,
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
