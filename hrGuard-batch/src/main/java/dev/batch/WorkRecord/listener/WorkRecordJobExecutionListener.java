package dev.batch.WorkRecord.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * WorkRecord Job/Step 시작·종료 리스너.
 *
 * <p>{@link JobExecutionListener}와 {@link StepExecutionListener}를 함께 구현해
 * Job 수준 지표(성공/실패 카운터)와 Step 수준 요약 로그를 한 곳에서 관리합니다.</p>
 *
 * <p>노출 메트릭:</p>
 * <pre>
 *   workrecord_batch_job_total{status="success"}
 *   workrecord_batch_job_total{status="failure"}
 * </pre>
 */
@Slf4j
@Component
public class WorkRecordJobExecutionListener implements JobExecutionListener, StepExecutionListener {

    private final Counter jobSuccessCounter;
    private final Counter jobFailureCounter;

    public WorkRecordJobExecutionListener(MeterRegistry registry) {
        this.jobSuccessCounter = Counter.builder("workrecord.batch.job")
                .tag("status", "success")
                .description("WorkRecord job 성공 횟수")
                .register(registry);
        this.jobFailureCounter = Counter.builder("workrecord.batch.job")
                .tag("status", "failure")
                .description("WorkRecord job 실패 횟수")
                .register(registry);
    }

    // ── JobExecutionListener ─────────────────────────────────────────────────

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("===== [workRecordComputeJob 시작] targetDate={}, jobId={} =====",
                jobExecution.getJobParameters().getString("targetDate"),
                jobExecution.getJobId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Duration elapsed = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            jobSuccessCounter.increment();
            log.info("===== [workRecordComputeJob 완료] targetDate={}, 소요시간={}ms =====",
                    jobExecution.getJobParameters().getString("targetDate"),
                    elapsed.toMillis());
            return;
        }

        jobFailureCounter.increment();
        for (Throwable t : jobExecution.getAllFailureExceptions()) {
            log.error("===== [workRecordComputeJob 실패] targetDate={}, cause={}: {} =====",
                    jobExecution.getJobParameters().getString("targetDate"),
                    t.getClass().getSimpleName(), t.getMessage(), t);
        }
    }

    // ── StepExecutionListener ────────────────────────────────────────────────

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("===== [workRecordComputeStep 시작] targetDate={} =====",
                stepExecution.getJobParameters().getString("targetDate"));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long total = stepExecution.getWriteCount() + stepExecution.getFilterCount();
        String successRate = total > 0
                ? String.format("%.1f", (double) stepExecution.getWriteCount() / total * 100.0)
                : "0.0";

        log.info("""
                        ===== [workRecordComputeStep 완료] =====
                          상태          : {}
                          읽은 인원     : {} 명
                          산출 완료     : {} 명
                          슬롯 없음 skip: {} 명
                          롤백          : {} 회
                          성공률        : {}%
                        =====================================""",
                stepExecution.getStatus(),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getFilterCount(),
                stepExecution.getRollbackCount(),
                successRate);

        return stepExecution.getExitStatus();
    }
}
