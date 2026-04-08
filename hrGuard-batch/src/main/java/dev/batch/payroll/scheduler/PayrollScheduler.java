package dev.batch.payroll.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 매월 말일 23:00에 급여 정산 배치를 실행하는 스케줄러.
 *
 * <p><b>JobParameter identifying 전략</b></p>
 * <pre>
 *   yearMonth  (identifying=true)  → Job Instance 구분 키 (비즈니스 식별자)
 *   run.id     (identifying=false) → 실패한 Job의 재실행을 허용하기 위한 타임스탬프
 * </pre>
 *
 * <p>이 구조의 효과:</p>
 * <ul>
 *   <li>같은 yearMonth로 COMPLETED된 Job이 있으면 Spring Batch가 중복 실행을 차단</li>
 *   <li>FAILED된 Job은 run.id가 달라져 새 JobExecution으로 재시도 가능</li>
 *   <li>chunk 처리 확인: BATCH_STEP_EXECUTION 테이블의 COMMIT_COUNT 컬럼으로 검증</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PayrollScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final JobExplorer jobExplorer;  // 이전 실행 이력 조회용

    @Scheduled(cron = "0 0 23 L * *", zone = "Asia/Seoul")
    public void runPayrollJob() throws Exception {
        YearMonth targetMonth = YearMonth.now(KST);

        JobParameters jobParameters = new JobParametersBuilder()
                // ── identifying=true (기본값): Job Instance 구분 키
                // 같은 yearMonth가 COMPLETED 상태면 Spring Batch가 재실행 자동 차단
                .addString("yearMonth", targetMonth.toString())
                // ── identifying=false: 인스턴스 구분에 사용 안 함
                // FAILED 상태의 Job을 run.id만 바꿔서 새 JobExecution으로 재시도 가능하게 함
                .addLong("run.id", System.currentTimeMillis(), false)
                .toJobParameters();

        // 실행 전 이전 이력 조회 → 중복 실행 방어 로그
        logPreviousExecutions(targetMonth.toString());

        log.info("[PayrollScheduler] 급여 배치 시작 → yearMonth={} (identifying), run.id=non-identifying",
                targetMonth);

        JobExecution execution = jobLauncher.run(jobRegistry.getJob("payrollJob"), jobParameters);

        log.info("[PayrollScheduler] 급여 배치 종료 → status={}, jobExecutionId={}",
                execution.getStatus(), execution.getId());
    }

    /**
     * 동일 yearMonth에 대한 이전 실행 이력을 조회해 로그로 남깁니다.
     * COMPLETED 이력이 있으면 Spring Batch가 JobInstanceAlreadyCompleteException을 던집니다.
     */
    private void logPreviousExecutions(String yearMonth) {
        try {
            jobExplorer.findJobInstancesByJobName("payrollJob", 0, 5)
                    .stream()
                    .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                    .filter(exec -> yearMonth.equals(exec.getJobParameters().getString("yearMonth")))
                    .forEach(exec -> log.info(
                            "[PayrollScheduler] 이전 실행 이력 → jobExecutionId={}, status={}, exitCode={}",
                            exec.getId(), exec.getStatus(), exec.getExitStatus().getExitCode()));
        } catch (Exception e) {
            log.warn("[PayrollScheduler] 이전 실행 이력 조회 실패: {}", e.getMessage());
        }
    }
}
