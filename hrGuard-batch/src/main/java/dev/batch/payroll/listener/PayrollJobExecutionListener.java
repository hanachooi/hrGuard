package dev.batch.payroll.listener;

import dev.batch.common.exception.BatchErrorClassifier;
import dev.batch.common.exception.BatchErrorClassifier.Classification;
import dev.batch.common.exception.BatchException;
import dev.payroll.service.InsuranceCalculator;
import dev.payroll.service.TaxCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Payroll Job 시작/종료 리스너.
 *
 * <p>Job 종료 시점에 {@link JobExecution}에 등록된 실패 원인을 {@link BatchErrorClassifier}에 위임하여
 * 분류된 코드/메시지로 구조화된 오류 로그를 남기고 Micrometer 카운터를 증가시킵니다.</p>
 *
 * <h3>OS Exit Code</h3>
 * Job 종료 시 {@link ExitCodeGenerator}로 OS 종료 코드를 결정한다.
 * <ul>
 *   <li>0 — 정상 (skip 0건)</li>
 *   <li>1 — 비정상 종료 (FAILED/STOPPED/UNKNOWN, 스케줄러가 실패로 감지)</li>
 *   <li>2 — 정상 종료지만 일부 데이터 skip 발생 (Warning, 운영자 점검 필요)</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollJobExecutionListener implements JobExecutionListener, ExitCodeGenerator {

    private volatile int exitCode = 0;

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

        // 모든 로그 라인에 job 컨텍스트 자동 포함 (logback PATTERN의 %X{} 변수)
        // 멀티스레드 Step 전환 시: TaskExecutor에 MDCTaskDecorator 등록 필요
        MDC.put("jobName", "payrollJob");
        MDC.put("jobParam", yearMonth != null ? yearMonth : "");

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
      try {
        insuranceCalculator.clear();
        log.info("4대보험 요율 메모리 해제 완료");
        taxCalculator.clear();
        log.info("간이세액표 메모리 해제 완료");

        Duration elapsed = Duration.between(
                jobExecution.getStartTime(), jobExecution.getEndTime());
        String yearMonth = jobExecution.getJobParameters().getString("yearMonth");

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            jobSuccessCounter.increment();
            long skipTotal = totalSkipCount(jobExecution);
            this.exitCode = skipTotal > 0 ? 2 : 0;
            log.info("===== [payrollJob 완료] yearMonth={}, 소요시간={}ms, skip={}건, exitCode={} =====",
                    yearMonth, elapsed.toMillis(), skipTotal, this.exitCode);
            return;
        }

        // ── 실패 케이스 : 원인 분석 ─────────────────────────────────────────
        jobFailureCounter.increment();
        this.exitCode = 1;

        for (Throwable throwable : jobExecution.getAllFailureExceptions()) {
            Classification c = BatchErrorClassifier.classify(throwable);
            log.error("[{}] {} | type={} | yearMonth={} | cause={}: {}",
                    c.code(), c.message(), c.type(),
                    yearMonth,
                    c.cause().getClass().getSimpleName(), c.cause().getMessage(),
                    throwable);
        }

        log.error("===== [payrollJob 비정상종료] status={}, yearMonth={}, 소요시간={}ms, exitCode=1 =====",
                jobExecution.getStatus(), yearMonth, elapsed.toMillis());
      } finally {
          MDC.clear();
      }
    }

    /** 모든 Step 의 read/process/write skip 합계. */
    private long totalSkipCount(JobExecution jobExecution) {
        long total = 0;
        for (StepExecution step : jobExecution.getStepExecutions()) {
            total += step.getReadSkipCount()
                  + step.getProcessSkipCount()
                  + step.getWriteSkipCount();
        }
        return total;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    // ── 호환용 (BatchException 직접 처리할 일이 있다면 사용) ────────────────

    @SuppressWarnings("unused")
    private static boolean isClassified(Throwable t) {
        return t instanceof BatchException;
    }
}
