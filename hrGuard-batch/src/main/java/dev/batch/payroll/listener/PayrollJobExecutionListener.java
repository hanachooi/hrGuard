package dev.batch.payroll.listener;

import dev.batch.common.exception.BatchErrorCode;
import dev.batch.common.exception.BatchException;
import dev.payroll.service.InsuranceCalculator;
import dev.payroll.service.TaxCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Payroll Job 시작/종료 리스너.
 *
 * <p>API 모듈의 {@code GlobalExceptionHandler}에 대응합니다.
 * Job 종료 시점에 {@link JobExecution}에 등록된 실패 원인을 분석하여
 * 구조화된 오류 로그를 남기고 Micrometer 카운터를 증가시킵니다.</p>
 *
 * <h3>감지 대상</h3>
 * <ul>
 *   <li>{@link OutOfMemoryError} — JVM 힙 고갈</li>
 *   <li>DB 관련 예외 — {@code DataAccessException} 계열</li>
 *   <li>{@link BatchException} — 배치 비즈니스 예외</li>
 *   <li>그 외 모든 {@link Throwable} — UNEXPECTED_ERROR 처리</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollJobExecutionListener implements JobExecutionListener {

    private final Counter jobSuccessCounter;
    private final Counter jobFailureCounter;
    private final InsuranceCalculator insuranceCalculator;
    private final TaxCalculator taxCalculator;

    public PayrollJobExecutionListener(MeterRegistry meterRegistry,
                                       InsuranceCalculator insuranceCalculator,
                                       TaxCalculator taxCalculator) {
        this.jobSuccessCounter = Counter.builder("payroll.batch.job")
                .tag("status", "success")
                .description("Payroll batch job 성공 횟수")
                .register(meterRegistry);
        this.jobFailureCounter = Counter.builder("payroll.batch.job")
                .tag("status", "failure")
                .description("Payroll batch job 실패 횟수")
                .register(meterRegistry);
        this.insuranceCalculator = insuranceCalculator;
        this.taxCalculator = taxCalculator;
    }

    // ── Job 시작 ────────────────────────────────────────────────────────────

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String yearMonth = jobExecution.getJobParameters().getString("yearMonth");
        LocalDate payrollDate = YearMonth.parse(yearMonth).atDay(1);
        insuranceCalculator.load(payrollDate);
        log.info("4대보험 요율 메모리 적재 완료 (기준일={})", payrollDate);
        taxCalculator.load(payrollDate);
        log.info("간이세액표 메모리 적재 완료 (기준일={})", payrollDate);

        log.info("===== [payrollJob 시작] yearMonth={}, jobId={} =====",
                yearMonth,
                jobExecution.getJobId());
    }

    // ── Job 종료 ────────────────────────────────────────────────────────────

    @Override
    public void afterJob(JobExecution jobExecution) {
        insuranceCalculator.clear();
        log.info("4대보험 요율 메모리 해제 완료");
        taxCalculator.clear();
        log.info("간이세액표 메모리 해제 완료");

        Duration elapsed = Duration.between(
                jobExecution.getStartTime(), jobExecution.getEndTime());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            jobSuccessCounter.increment();
            log.info("===== [payrollJob 완료] yearMonth={}, 소요시간={}ms =====",
                    jobExecution.getJobParameters().getString("yearMonth"),
                    elapsed.toMillis());
            return;
        }

        // ── 실패 케이스 : 원인 분석 ─────────────────────────────────────────
        jobFailureCounter.increment();

        // JobExecution에 등록된 모든 예외를 순회하여 분류
        for (Throwable throwable : jobExecution.getAllFailureExceptions()) {
            classify(throwable, jobExecution);
        }

        log.error("===== [payrollJob 비정상종료] status={}, yearMonth={}, 소요시간={}ms =====",
                jobExecution.getStatus(),
                jobExecution.getJobParameters().getString("yearMonth"),
                elapsed.toMillis());
    }

    // ── 예외 분류 ────────────────────────────────────────────────────────────

    private void classify(Throwable throwable, JobExecution jobExecution) {
        Throwable root = unwrap(throwable);

        if (root instanceof OutOfMemoryError oom) {
            handleOom(oom, jobExecution);
        } else if (root instanceof BatchException batchEx) {
            handleBatchException(batchEx, jobExecution);
        } else if (isDataAccessException(root)) {
            handleDatabaseError(root, jobExecution);
        } else {
            handleUnexpected(root, jobExecution);
        }
    }

    /**
     * OOM — 힙 부족. 청크 크기 축소 또는 힙 증설 필요
     */
    private void handleOom(OutOfMemoryError oom, JobExecution jobExecution) {
        BatchException wrapped = new BatchException(BatchErrorCode.OUT_OF_MEMORY, oom);
        log.error("[{}] {} | yearMonth={} | 조치: -Xmx 증설 또는 chunk-size 축소",
                wrapped.getCommonError().getCode(),
                wrapped.getCommonError().getMessage(),
                jobExecution.getJobParameters().getString("yearMonth"),
                oom);
    }

    /**
     * BatchException — 배치 비즈니스 오류
     */
    private void handleBatchException(BatchException ex, JobExecution jobExecution) {
        log.error("[{}] {} | yearMonth={} | code={}",
                ex.getCommonError().getCode(),
                ex.getCommonError().getMessage(),
                jobExecution.getJobParameters().getString("yearMonth"),
                ex.getCommonError().getCode(),
                ex);
    }

    /**
     * DataAccessException 계열 — DB 연결·쿼리 오류
     */
    private void handleDatabaseError(Throwable ex, JobExecution jobExecution) {
        BatchException wrapped = new BatchException(BatchErrorCode.DATABASE_ERROR, ex);
        log.error("[{}] {} | yearMonth={} | cause={}",
                wrapped.getCommonError().getCode(),
                wrapped.getCommonError().getMessage(),
                jobExecution.getJobParameters().getString("yearMonth"),
                ex.getMessage(),
                ex);
    }

    /**
     * 그 외 치명적 오류
     */
    private void handleUnexpected(Throwable ex, JobExecution jobExecution) {
        BatchException wrapped = new BatchException(BatchErrorCode.UNEXPECTED_ERROR, ex);
        log.error("[{}] {} | yearMonth={} | cause={}",
                wrapped.getCommonError().getCode(),
                wrapped.getCommonError().getMessage(),
                jobExecution.getJobParameters().getString("yearMonth"),
                ex.getMessage(),
                ex);
    }

    // ── 유틸리티 ─────────────────────────────────────────────────────────────

    /**
     * 중첩된 예외를 끝까지 풀어 근본 원인(root cause)을 반환합니다.
     * 순환 참조 방어를 위해 최대 10단계까지만 탐색합니다.
     */
    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current.getCause() != null && current.getCause() != current && depth < 10) {
            current = current.getCause();
            depth++;
        }
        return current;
    }

    /**
     * Spring의 {@code DataAccessException} 계열 여부를 클래스 이름으로 판단합니다.
     * (hrGuard-batch가 spring-data 의존성을 갖지 않는 경우를 고려한 안전한 검사)
     */
    private boolean isDataAccessException(Throwable throwable) {
        Class<?> clazz = throwable.getClass();
        while (clazz != null) {
            if ("org.springframework.dao.DataAccessException".equals(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}
