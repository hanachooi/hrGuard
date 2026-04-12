package dev.batch.payroll.step;

import dev.batch.common.exception.BatchException;
import dev.batch.payroll.listener.*;
import dev.common.configuration.TransactionManagerConfig;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import dev.payroll.repository.MonthlyPayrollRepository;
import dev.payroll.repository.PayrollItemRepository;
import dev.payroll.service.WageCalculator;
import dev.payroll.service.WorkTimeResult;
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkRecord 일별 집계 → MonthlyPayroll 정산 Step.
 *
 * <p>WorkRecord에 이미 배치(WorkRecordComputeProcessor)가 산출한
 * regularMinutes, overtimeMinutes, nightMinutes, holidayMinutes, holidayOvertimeMinutes가
 * 저장되어 있으므로, 이 Step은 단순히 분→시간 변환 후 WageCalculator에 위임합니다.</p>
 *
 * <h3>TimeSegmentSplitter 제거 근거</h3>
 * <p>기존에는 WorkRecord의 interval(startTime~endTime)을 순회하며
 * TimeSegmentSplitter가 연장/야간/휴일을 실시간 계산했습니다.
 * 리팩토링 후에는 계산이 WorkRecord 배치에서 완료되므로
 * 정산 배치는 조회→변환→저장만 수행합니다.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PayrollStepConfig {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 50;
    private static final int RETRY_LIMIT = 3;

    private final JobRepository jobRepository;
    private final WorkRecordRepository workRecordRepository;
    private final MonthlyPayrollRepository monthlyPayrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WageCalculator wageCalculator;
    private final PayrollChunkListener payrollChunkListener;
    private final PayrollSkipListener payrollSkipListener;
    private final PayrollItemReadListener payrollItemReadListener;
    private final PayrollItemProcessListener payrollItemProcessListener;
    private final PayrollItemWriteListener payrollItemWriteListener;

    @Qualifier(TransactionManagerConfig.DOMAIN_TRANSACTION_MANAGER)
    private final PlatformTransactionManager domainTransactionManager;

    @Bean
    public Step payrollStep(
            ListItemReader<Long> payrollReader,
            ItemProcessor<Long, MonthlyPayroll> payrollProcessor,
            ItemWriter<MonthlyPayroll> payrollWriter
    ) {
        return new StepBuilder("payrollStep", jobRepository)
                .<Long, MonthlyPayroll>chunk(CHUNK_SIZE, domainTransactionManager)
                .reader(payrollReader)
                .processor(payrollProcessor)
                .writer(payrollWriter)
                .listener((Object) payrollChunkListener)
                .listener(payrollStepListener())
                .listener((Object) payrollItemReadListener)
                .listener((Object) payrollItemProcessListener)
                .listener((Object) payrollItemWriteListener)
                .faultTolerant()
                .skip(BatchException.class)
                .skipLimit(SKIP_LIMIT)
                .noSkip(Error.class)
                .retry(TransientDataAccessException.class)
                .retryLimit(RETRY_LIMIT)
                .listener(payrollSkipListener)
                .build();
    }

    @Bean
    public StepExecutionListener payrollStepListener() {
        return new StepExecutionListener() {

            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info("===== [payrollStep 시작] chunk_size={} =====", CHUNK_SIZE);
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                long totalProcessed = stepExecution.getWriteCount() + stepExecution.getFilterCount();
                String successRate = totalProcessed > 0
                        ? String.format("%.1f", (double) stepExecution.getWriteCount() / totalProcessed * 100.0)
                        : "0.0";

                log.info("""
                                ===== [payrollStep 완료] =====
                                  상태            : {}
                                  처리 청크 수    : {} 건
                                  읽은 인원       : {} 명
                                  정산 완료       : {} 명
                                  스케줄 없음 skip: {} 명
                                  롤백            : {} 회
                                  정산 성공률     : {}%
                                ===============================""",
                        stepExecution.getStatus(),
                        stepExecution.getCommitCount(),
                        stepExecution.getReadCount(),
                        stepExecution.getWriteCount(),
                        stepExecution.getFilterCount(),
                        stepExecution.getRollbackCount(),
                        successRate
                );
                return stepExecution.getExitStatus();
            }
        };
    }

    /**
     * 해당 월에 WorkRecord가 있는 memberId 목록 조회.
     */
    @Bean
    @StepScope
    public ListItemReader<Long> payrollReader(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<Long> memberIds = workRecordRepository.findDistinctMemberIdsByBizDateBetween(
                ym.atDay(1), ym.atEndOfMonth());
        log.info("급여 계산 대상 인원: {}명 ({})", memberIds.size(), yearMonth);
        return new ListItemReader<>(memberIds);
    }

    /**
     * WorkRecord 일별 집계 → MonthlyPayroll 생성.
     *
     * <p>WorkRecord에 이미 regularMinutes, overtimeMinutes, nightMinutes,
     * holidayMinutes, holidayOvertimeMinutes가 저장되어 있으므로
     * 시간 단위로 변환 후 WageCalculator에 위임합니다.</p>
     *
     * <h3>skip 조건 (null 반환)</h3>
     * <ul>
     *   <li>WorkSchedule 없음 (시급 정보 부재)</li>
     * </ul>
     */
    @Bean
    @StepScope
    public ItemProcessor<Long, MonthlyPayroll> payrollProcessor(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        return memberId -> {
            YearMonth ym = YearMonth.parse(yearMonth);

            WorkSchedule schedule = workScheduleRepository.findByMemberId(memberId).orElse(null);
            if (schedule == null) {
                log.warn("WorkSchedule 없음, skip: memberId={}", memberId);
                return null;
            }
            int hourlyWage = schedule.getHourlyWage();

            List<WorkRecord> records = workRecordRepository.findByMemberIdAndBizDateBetween(
                    memberId, ym.atDay(1), ym.atEndOfMonth());

            MonthlyPayroll payroll = MonthlyPayroll.builder()
                    .memberId(memberId)
                    .year(ym.getYear())
                    .month(ym.getMonthValue())
                    .build();

            List<PayrollItem> allItems = new ArrayList<>();

            for (WorkRecord record : records) {
                // WorkRecord 집계 필드(분)를 시간(double)으로 변환해 WageCalculator에 위임
                WorkTimeResult result = new WorkTimeResult(
                        record.getRegularMinutes() / 60.0,
                        record.getOvertimeMinutes() / 60.0,
                        record.getNightMinutes() / 60.0,
                        record.getHolidayMinutes() / 60.0,
                        record.getHolidayOvertimeMinutes() / 60.0
                );
                allItems.addAll(wageCalculator.calculate(result, hourlyWage, payroll));
            }

            long total = allItems.stream().mapToLong(PayrollItem::getAmount).sum();
            payroll.updateTotalAmount(total);
            payroll.setPendingItems(allItems);
            return payroll;
        };
    }

    @Bean
    public ItemWriter<MonthlyPayroll> payrollWriter() {
        return chunk -> {
            for (MonthlyPayroll payroll : chunk.getItems()) {
                monthlyPayrollRepository.save(payroll);
                payrollItemRepository.saveAll(payroll.getPendingItems());
                log.info("급여 저장 완료: memberId={}, {}년 {}월, 총액={}원",
                        payroll.getMemberId(), payroll.getYear(), payroll.getMonth(), payroll.getTotalAmount());
            }
        };
    }
}
