package dev.batch.payroll.step;

import dev.batch.common.exception.BatchException;
import dev.batch.payroll.listener.PayrollSkipListener;
import dev.common.configuration.TransactionManagerConfig;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import dev.workrecord.entity.WorkRecord;
import dev.workschedule.entity.WorkSchedule;
import dev.payroll.repository.MonthlyPayrollRepository;
import dev.payroll.repository.PayrollItemRepository;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.repository.WorkScheduleRepository;
import dev.payroll.service.TimeSegmentSplitter;
import dev.payroll.service.WageCalculator;
import dev.payroll.service.WorkTimeResult;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PayrollStepConfig {

    private static final int CHUNK_SIZE = 100;
    /**
     * Processor skip 허용 최대 건수. 초과 시 Job 자체를 FAILED로 중단.
     */
    private static final int SKIP_LIMIT = 50;
    /**
     * Writer DB 오류 최대 재시도 횟수 (지수 백오프 없이 단순 retry).
     */
    private static final int RETRY_LIMIT = 3;
    private final JobRepository jobRepository;
    private final WorkRecordRepository workRecordRepository;
    private final MonthlyPayrollRepository monthlyPayrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final TimeSegmentSplitter timeSegmentSplitter;
    private final WageCalculator wageCalculator;
    private final PayrollChunkListener payrollChunkListener;
    private final PayrollSkipListener payrollSkipListener;
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
                .listener((Object) payrollChunkListener)   // chunk 단위 진행률 추적 + 퇴근미기록 카운터 리셋
                .listener(payrollStepListener())  // step 시작/종료 요약
                // ── Fault-Tolerant 정책 ──────────────────────────────────
                .faultTolerant()
                // skip 허용 대상: BatchException (스케줄 없음 등 비즈니스 skip)
                .skip(BatchException.class)
                .skipLimit(SKIP_LIMIT)
                // skip 금지 대상: Error 계열 (OOM 등 시스템 장애는 즉시 Job 중단)
                .noSkip(Error.class)
                // retry 대상: 일시적 DB 오류 (네트워크 순단, 락 타임아웃 등)
                .retry(TransientDataAccessException.class)
                .retryLimit(RETRY_LIMIT)
                // skip 발생 시 원인 분류 로그
                .listener(payrollSkipListener)
                .build();
    }

    /**
     * Step 시작/종료 시점의 요약 리스너.
     *
     * <p>afterStep에서 StepExecution의 최종 카운터를 읽어 전체 정산 결과를 출력합니다.
     * commitCount가 곧 '성공적으로 처리된 청크 수'이며, 각 청크는 하나의 트랜잭션입니다.</p>
     */
    @Bean
    public StepExecutionListener payrollStepListener() {
        return new StepExecutionListener() {

            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info("===== [payrollStep 시작] chunk_size={} =====",
                        CHUNK_SIZE);
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
                                  처리 청크 수    : {} 건  (트랜잭션 커밋 {}회)
                                  읽은 인원       : {} 명
                                  정산 완료       : {} 명
                                  멤버 skip       : {} 명  (스케줄 없음)
                                  퇴근 미기록     : {} 건  (해당 일만 제외 후 부분 정산)
                                  롤백            : {} 회
                                  정산 성공률     : {}%
                                ===============================""",
                        stepExecution.getStatus(),
                        stepExecution.getCommitCount(),
                        stepExecution.getCommitCount(),
                        stepExecution.getReadCount(),
                        stepExecution.getWriteCount(),
                        stepExecution.getFilterCount(),
                        payrollChunkListener.getCommuteSkippedCount(),
                        stepExecution.getRollbackCount(),
                        successRate
                );
                return stepExecution.getExitStatus(); // 기존 ExitStatus 유지
            }
        };
    }

    // 해당 월에 근무 기록(WorkRecord)이 있는 memberId 목록을 읽어옴
    // Commute(출입 기록)가 아닌 WorkRecord(실제 근무 기록) 기준으로 대상자 선정
    @Bean
    @StepScope
    public ListItemReader<Long> payrollReader(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<Long> memberIds = workRecordRepository.findDistinctMemberIdsByBizDateBetween(
                ym.atDay(1), ym.atEndOfMonth()
        );
        log.info("급여 계산 대상 인원: {}명 ({})", memberIds.size(), yearMonth);
        return new ListItemReader<>(memberIds);
    }

    /**
     * memberId 1명의 해당 월 근무 기록을 읽어 MonthlyPayroll을 생성합니다.
     *
     * <p>같은 날 외근·출장 등으로 여러 세그먼트가 있어도 일별 합산 후 정확히 계산합니다.</p>
     *
     * <h3>처리 흐름</h3>
     * <pre>
     *   WorkRecord 전체 조회 → biz_date 기준 그룹핑
     *   → 날짜별 세그먼트 합산 → TimeSegmentSplitter.splitDaily()
     *   → 연장/야간/휴일 수당 계산
     * </pre>
     *
     * <h3>skip 조건 (null 반환)</h3>
     * <ul>
     *   <li>WorkSchedule 없음</li>
     * </ul>
     *
     * <h3>부분 제외 (해당 날만 skip)</h3>
     * <ul>
     *   <li>특정 날의 모든 세그먼트가 endTime == null → 해당 날만 제외, 나머지 정산 진행</li>
     * </ul>
     */
    @Bean
    @StepScope
    public ItemProcessor<Long, MonthlyPayroll> payrollProcessor(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        return memberId -> {
            YearMonth ym = YearMonth.parse(yearMonth);

            // ── 근무 스케줄 조회 (소정 근무 요일 + 일 근로시간 + 시급) ──────────
            WorkSchedule schedule = workScheduleRepository.findByMemberId(memberId)
                    .orElse(null);
            if (schedule == null) {
                log.warn("WorkSchedule 없음, skip: memberId={}", memberId);
                return null;
            }

            Set<DayOfWeek> scheduledWorkDays = schedule.getWorkDaysAsSet();
            double dailyWorkHours = schedule.getDailyWorkHours();
            int hourlyWage = schedule.getHourlyWage();

            // ── 근무 기록 조회 및 일별 그룹핑 ───────────────────────────
            List<WorkRecord> records = workRecordRepository
                    .findByMemberIdAndBizDateBetweenOrderByBizDateAscStartTimeAsc(
                            memberId, ym.atDay(1), ym.atEndOfMonth());

            // biz_date 기준으로 그룹핑 (하루에 외근·출장 등 여러 세그먼트 존재 가능)
            Map<LocalDate, List<WorkRecord>> byDate = records.stream()
                    .collect(Collectors.groupingBy(WorkRecord::getBizDate));

            MonthlyPayroll payroll = MonthlyPayroll.builder()
                    .memberId(memberId)
                    .year(ym.getYear())
                    .month(ym.getMonthValue())
                    .build();

            List<PayrollItem> allItems = new ArrayList<>();

            for (Map.Entry<LocalDate, List<WorkRecord>> entry : byDate.entrySet()) {
                LocalDate bizDate = entry.getKey();
                List<WorkRecord> segs = entry.getValue();

                // 해당 날 모든 세그먼트가 종료 미기록이면 날만 skip
                boolean hasValidSeg = segs.stream().anyMatch(s -> s.getEndTime() != null);
                if (!hasValidSeg) {
                    log.warn("근무 종료 미기록 skip: memberId={}, bizDate={}", memberId, bizDate);
                    payrollChunkListener.incrementCommuteSkipped();
                    continue;
                }

                // 일별 합산 계산 (세그먼트 N건 → WorkTimeResult 1건)
                WorkTimeResult result = timeSegmentSplitter.splitDaily(
                        bizDate, segs, scheduledWorkDays, dailyWorkHours);

                allItems.addAll(wageCalculator.calculate(result, hourlyWage, payroll));
            }

            long total = allItems.stream().mapToLong(PayrollItem::getAmount).sum();
            payroll.updateTotalAmount(total);
            payroll.setPendingItems(allItems);
            return payroll;
        };
    }

    // MonthlyPayroll 저장 후 PayrollItem 별도 저장
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
