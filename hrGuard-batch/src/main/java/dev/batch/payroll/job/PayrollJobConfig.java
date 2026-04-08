package dev.batch.payroll.job;

import dev.batch.payroll.listener.PayrollJobExecutionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Configuration
@RequiredArgsConstructor
public class PayrollJobConfig {

    private final JobRepository jobRepository;
    private final PayrollJobExecutionListener payrollJobExecutionListener;

    @Bean
    public Job payrollJob(Step payrollStep) {
        return new JobBuilder("payrollJob", jobRepository)
                .validator(payrollJobParametersValidator()) // 실행 전 파라미터 검증
                .listener(payrollJobExecutionListener)      // Job 시작/종료 + 예외 분류
                .start(payrollStep)
                .build();
    }

    /**
     * JobParameters 검증기.
     *
     * <p>배치가 실행되기 전에 파라미터를 검사합니다.</p>
     * <ul>
     *   <li>yearMonth (identifying=true) : 필수값, "YYYY-MM" 형식이어야 함</li>
     *   <li>run.id    (identifying=false): 선택값, 재실행 타임스탬프 (형식 검증 불필요)</li>
     * </ul>
     */
    @Bean
    public JobParametersValidator payrollJobParametersValidator() {
        return new JobParametersValidator() {
            @Override
            public void validate(JobParameters parameters) throws JobParametersInvalidException {
                // ── yearMonth 필수 체크
                String yearMonth = parameters.getString("yearMonth");
                if (yearMonth == null || yearMonth.isBlank()) {
                    throw new JobParametersInvalidException(
                            "필수 파라미터 누락: yearMonth (형식: YYYY-MM, identifying=true)");
                }

                // ── yearMonth 형식 체크 (YYYY-MM)
                try {
                    YearMonth.parse(yearMonth);
                } catch (DateTimeParseException e) {
                    throw new JobParametersInvalidException(
                            "잘못된 yearMonth 형식: '" + yearMonth + "' → 올바른 형식: YYYY-MM (예: 2026-04)");
                }

                // ── 미래 월 실행 방어
                if (YearMonth.parse(yearMonth).isAfter(YearMonth.now())) {
                    throw new JobParametersInvalidException(
                            "미래 월은 정산할 수 없습니다: yearMonth=" + yearMonth);
                }
            }
        };
    }
}
