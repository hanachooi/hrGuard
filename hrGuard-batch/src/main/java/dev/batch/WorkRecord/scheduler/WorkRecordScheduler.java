package dev.batch.WorkRecord.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Commute → WorkRecord(OFFICE) 동기화 일일 스케줄러.
 *
 * <h3>실행 시각 선택 기준</h3>
 * <ul>
 *   <li>01:00 — 자정을 넘기는 야간 근무자(23:00 퇴근 등)의 outTime 기록이
 *       완전히 커밋된 후 처리 가능</li>
 *   <li>월급 배치(payrollJob)보다 반드시 먼저 실행되어야 WorkRecord가 채워진다.
 *       payrollJob은 매월 1일 02:00 으로 설정하면 충분한 선행 여유가 확보된다.</li>
 * </ul>
 *
 * <h3>재처리</h3>
 * 특정 날짜 재처리가 필요할 때는 직접 Job 파라미터로 실행한다:
 * <pre>
 *   ./gradlew :hrGuard-batch:bootRun \
 *     --args="--spring.batch.job.name=workRecordSyncJob targetDate=2026-04-01 run.id=re.1"
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkRecordScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("workRecordSyncJob")
    private final Job workRecordSyncJob;

    /**
     * 매일 01:00 — 전날 Commute를 WorkRecord(OFFICE)로 동기화.
     * cron = "초 분 시 일 월 요일"
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void syncYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        run(yesterday, "scheduled");
    }

    // ── 수동 트리거 (컨트롤러나 테스트에서 호출 가능) ────────────────────────────

    public void runForDate(LocalDate targetDate) {
        run(targetDate, "manual." + targetDate);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private void run(LocalDate targetDate, String runId) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("targetDate", targetDate.toString(), true)  // identifying
                    .addString("run.id", runId, false) // non-identifying
                    .toJobParameters();

            var execution = jobLauncher.run(workRecordSyncJob, params);
            log.info("[WorkRecordSync] 실행 완료: date={}, status={}, " +
                            "읽음={}, 처리={}, 스킵={}",
                    targetDate,
                    execution.getStatus(),
                    execution.getStepExecutions().stream()
                            .mapToLong(s -> s.getReadCount()).sum(),
                    execution.getStepExecutions().stream()
                            .mapToLong(s -> s.getWriteCount()).sum(),
                    execution.getStepExecutions().stream()
                            .mapToLong(s -> s.getSkipCount()).sum());

        } catch (Exception e) {
            log.error("[WorkRecordSync] 실행 실패: date={}, cause={}", targetDate, e.getMessage(), e);
        }
    }
}
