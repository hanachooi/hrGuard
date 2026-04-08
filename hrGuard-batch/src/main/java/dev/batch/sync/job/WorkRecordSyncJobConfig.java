package dev.batch.sync.job;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Commute → WorkRecord(OFFICE) 동기화 Job.
 *
 * <h3>실행 방법</h3>
 * <pre>
 *   # 어제 데이터 처리 (스케줄러 자동 실행)
 *   ./gradlew :hrGuard-batch:bootRun \
 *     --args="--spring.batch.job.name=workRecordSyncJob targetDate=2026-04-04"
 *
 *   # 특정 날짜 재처리 (idempotent — 기존 OFFICE 레코드 삭제 후 재생성)
 *   ./gradlew :hrGuard-batch:bootRun \
 *     --args="--spring.batch.job.name=workRecordSyncJob targetDate=2026-04-01 run.id=re.1"
 * </pre>
 *
 * <h3>파라미터</h3>
 * <ul>
 *   <li>{@code targetDate} (identifying=true) : 처리 대상 날짜 (YYYY-MM-DD, 오늘 포함 미래 불가)</li>
 *   <li>{@code run.id}    (identifying=false): 재실행 식별자</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WorkRecordSyncJobConfig {

    private final JobRepository jobRepository;

    @Bean
    public Job workRecordSyncJob(Step workRecordSyncStep) {
        return new JobBuilder("workRecordSyncJob", jobRepository)
                .validator(workRecordSyncJobParametersValidator())
                .start(workRecordSyncStep)
                .build();
    }

    @Bean
    public JobParametersValidator workRecordSyncJobParametersValidator() {
        return new JobParametersValidator() {
            @Override
            public void validate(JobParameters parameters) throws JobParametersInvalidException {
                String targetDate = parameters.getString("targetDate");

                if (targetDate == null || targetDate.isBlank()) {
                    throw new JobParametersInvalidException(
                            "필수 파라미터 누락: targetDate (형식: YYYY-MM-DD)");
                }

                LocalDate date;
                try {
                    date = LocalDate.parse(targetDate);
                } catch (DateTimeParseException e) {
                    throw new JobParametersInvalidException(
                            "잘못된 targetDate 형식: '" + targetDate + "' → 올바른 형식: YYYY-MM-DD");
                }

                // 오늘 이후 날짜는 Commute 데이터가 완전하지 않을 수 있으므로 방어
                if (date.isAfter(LocalDate.now().minusDays(1))) {
                    throw new JobParametersInvalidException(
                            "targetDate는 어제 이전이어야 합니다: " + targetDate);
                }
            }
        };
    }
}
