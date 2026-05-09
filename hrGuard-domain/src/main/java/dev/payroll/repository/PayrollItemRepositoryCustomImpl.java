package dev.payroll.repository;

import dev.payroll.repository.projection.PayrollItemProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PayrollItemRepositoryCustomImpl implements PayrollItemRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchInsert(List<PayrollItemProjection> items) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO payroll_item (id, monthly_payroll_id, item_type, hours, amount) VALUES (?, ?, ?, ?, ?)",
                items,
                items.size(),
                (ps, item) -> {
                    ps.setLong(1, item.id());
                    ps.setLong(2, item.monthlyPayrollId());
                    ps.setString(3, item.itemType().name());
                    ps.setDouble(4, item.hours());
                    ps.setBigDecimal(5, item.amount());
                }
        );
    }
}
