package dev.batch.payroll.listener;

import dev.batch.common.exception.BatchErrorClassifier.Classification;
import dev.common.configuration.DataSourceConfig;
import dev.common.configuration.TransactionManagerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * payroll_error_log 적재 전용 컴포넌트.
 *
 * <p>SkipListener 안에서 직접 INSERT 하면 chunk 트랜잭션 retry-replay 시
 * 동일 행이 중복 적재되거나 롤백에 휘말려 누락될 수 있다.
 * REQUIRES_NEW 로 새 트랜잭션을 강제하여 chunk 트랜잭션과 격리한다.</p>
 *
 * <p>SkipListener 자기 호출은 Spring AOP 가 가로채지 못하므로
 * 반드시 별도 빈으로 분리해 외부 호출로 진입해야 트랜잭션이 적용된다.</p>
 */
@Slf4j
@Component
public class PayrollErrorLogWriter {

    private final JdbcTemplate jdbcTemplate;

    public PayrollErrorLogWriter(
            @Qualifier(DataSourceConfig.DOMAIN_DATASOURCE) DataSource domainDataSource) {
        this.jdbcTemplate = new JdbcTemplate(domainDataSource);
    }

    @Transactional(
            transactionManager = TransactionManagerConfig.DOMAIN_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void save(Long memberId, Integer year, Integer month,
                     String phase, Classification c, String originalDataJson) {
        try {
            jdbcTemplate.update("""
                INSERT INTO payroll_error_log
                    (member_id, `year`, `month`, phase, error_type, error_code, error_message, original_data, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                memberId, year, month, phase,
                c.type().name(), c.code(), c.message(), originalDataJson);
        } catch (Exception e) {
            log.error("[SKIP] payroll_error_log 저장 실패 — memberId={} phase={} cause={}",
                    memberId, phase, e.getMessage());
        }
    }
}
