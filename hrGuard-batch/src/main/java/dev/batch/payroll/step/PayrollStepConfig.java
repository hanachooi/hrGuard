package dev.batch.payroll.step;

import dev.batch.common.exception.BatchErrorCode;
import dev.batch.common.exception.BatchException;
import dev.batch.payroll.dto.PayrollInputDto;
import dev.batch.payroll.listener.PayrollChunkListener;
import dev.batch.payroll.listener.PayrollSkipListener;
import dev.common.configuration.DataSourceConfig;
import dev.common.configuration.TransactionManagerConfig;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.repository.MonthlyPayrollRepository;
import dev.payroll.repository.PayrollItemRepository;
import dev.payroll.service.*;
import dev.payrollpolicy.repository.PayrollPolicyRepository;
import dev.payrollpolicy.repository.projection.PayrollPolicyProjection;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workrecord.repository.projection.WorkRecordProjection;
import dev.workschedule.repository.WorkScheduleRepository;
import dev.workschedule.repository.projection.WorkScheduleProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PayrollStepConfig {

    private static final Logger heapLog = LoggerFactory.getLogger("dev.batch.heap");

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 50;
    private static final int RETRY_LIMIT = 3;

    /**
     * 근로기준법 제53조: 주 연장근로 최대 12시간
     */
    private static final double WEEKLY_OVERTIME_LIMIT = 12.0;

    private final JobRepository jobRepository;
    private final WorkRecordRepository workRecordRepository;
    private final MonthlyPayrollRepository monthlyPayrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final PayrollPolicyRepository payrollPolicyRepository;
    private final WageCalculator wageCalculator;
    private final InsuranceCalculator insuranceCalculator;
    private final TaxCalculator taxCalculator;
    private final PayrollChunkListener payrollChunkListener;
    private final PayrollSkipListener payrollSkipListener;
    @Qualifier(TransactionManagerConfig.DOMAIN_TRANSACTION_MANAGER)
    private final PlatformTransactionManager domainTransactionManager;
    @Qualifier(DataSourceConfig.DOMAIN_DATASOURCE)
    private final DataSource domainDataSource;

    @Bean
    public Step payrollStep(
            ItemReader<PayrollInputDto> payrollReader,
            JdbcPagingItemReader<Long> memberIdReader,
            ItemProcessor<PayrollInputDto, MonthlyPayroll> payrollProcessor,
            ItemWriter<MonthlyPayroll> payrollWriter
    ) {
        return new StepBuilder("payrollStep", jobRepository)
                .<PayrollInputDto, MonthlyPayroll>chunk(CHUNK_SIZE, domainTransactionManager)
                .reader(payrollReader)
                .processor(payrollProcessor)
                .writer(payrollWriter)
                .stream(memberIdReader)
                .listener((ChunkListener) payrollChunkListener)
                .listener((StepExecutionListener) payrollChunkListener)
                .listener(payrollStepListener())
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
                                  처리 청크 수    : {} 건  (트랜잭션 커밋 {}회)
                                  읽은 인원       : {} 명
                                  정산 완료       : {} 명
                                  멤버 skip       : {} 명  (스케줄 없음)
                                  롤백            : {} 회
                                  정산 성공률     : {}%
                                ===============================""",
                        stepExecution.getStatus(),
                        stepExecution.getCommitCount(),
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
     * memberId 만 Keyset 페이지 단위로 흘려보내는 delegate.
     * Step 의 .stream() 으로 등록되어 open/update/close 라이프사이클을 Spring Batch 가 관리한다.
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> memberIdReader(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("DISTINCT member_id");
        queryProvider.setFromClause("FROM work_record");
        queryProvider.setWhereClause("biz_date BETWEEN :from AND :to");
        queryProvider.setSortKeys(Map.of("member_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<Long>()
                .name("payrollMemberIdReader")
                .dataSource(domainDataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("from", from, "to", to))
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, n) -> rs.getLong(1))
                .saveState(true)
                .build();
    }

    /**
     * memberId 페이징 + chunk 단위 IN절 bulk 조회.
     * - delegate(memberIdReader)가 Keyset 기반으로 memberId 를 페이지 단위 제공
     * - fillBuffer(): CHUNK_SIZE 개 memberId 를 한 번에 수집한 뒤 3개 IN절 쿼리로 schedule/policy/records 일괄 조회
     * → chunk 당 3쿼리 (멤버별 단건 조회 대비 CHUNK_SIZE × 3 쿼리 절약)
     * - read(): buffer 에서 PayrollInputDto 를 순서대로 반환, 소진 시 다음 fillBuffer() 호출
     * - delegate 의 open/update/close 는 Step .stream() 등록을 통해 Spring Batch 가 호출
     */
    @Bean
    @StepScope
    public ItemReader<PayrollInputDto> payrollReader(
            JdbcPagingItemReader<Long> memberIdReader,
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        log.info("급여 계산 Reader 초기화: yearMonth={}, range=[{}~{}], pageSize={}",
                yearMonth, from, to, CHUNK_SIZE);

        AtomicInteger dtoCreatedTotal = new AtomicInteger();

        return new ItemReader<>() {
            private final List<PayrollInputDto> buffer = new ArrayList<>(CHUNK_SIZE);
            private int bufferIndex = 0;

            @Override
            public PayrollInputDto read() throws Exception {
                if (bufferIndex >= buffer.size()) {
                    if (!fillBuffer()) return null;
                }
                dtoCreatedTotal.incrementAndGet();
                return buffer.get(bufferIndex++);
            }

            private boolean fillBuffer() throws Exception {
                buffer.clear();
                bufferIndex = 0;

                List<Long> memberIds = new ArrayList<>(CHUNK_SIZE);
                Long id;
                while (memberIds.size() < CHUNK_SIZE && (id = memberIdReader.read()) != null) {
                    memberIds.add(id);
                }
                if (memberIds.isEmpty()) {
                    log.info("[Reader] 모든 페이지 소진 — 누적 PayrollInputDto 생성 수={}", dtoCreatedTotal.get());
                    return false;
                }

                long queryStart = System.nanoTime();

                Map<Long, WorkScheduleProjection> scheduleMap = workScheduleRepository
                        .findProjectionByMemberIdIn(memberIds)
                        .stream()
                        .collect(Collectors.toMap(WorkScheduleProjection::memberId, Function.identity()));

                Map<Long, PayrollPolicyProjection> policyMap = payrollPolicyRepository
                        .findProjectionByMemberIdIn(memberIds)
                        .stream()
                        .collect(Collectors.toMap(PayrollPolicyProjection::memberId, Function.identity()));

                Map<Long, List<WorkRecordProjection>> recordsMap = workRecordRepository
                        .findProjectionByMemberIdInAndBizDateBetween(memberIds, from, to)
                        .stream()
                        .collect(Collectors.groupingBy(WorkRecordProjection::memberId));

                long queryMs = (System.nanoTime() - queryStart) / 1_000_000;
                long heapMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
                log.info("[Reader] IN절 조회 완료 — memberIds={}건, schedule={}건, policy={}건, records={}건, 조회={}ms",
                        memberIds.size(), scheduleMap.size(), policyMap.size(),
                        recordsMap.values().stream().mapToInt(List::size).sum(), queryMs);
                heapLog.info("[HEAP] READER_END   total={} heap_mb={}", dtoCreatedTotal.get(), heapMb);

                for (Long memberId : memberIds) {
                    buffer.add(new PayrollInputDto(
                            memberId,
                            scheduleMap.get(memberId),
                            policyMap.get(memberId),
                            recordsMap.getOrDefault(memberId, List.of())
                    ));
                }
                return true;
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<PayrollInputDto, MonthlyPayroll> payrollProcessor(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        YearMonth ym = YearMonth.parse(yearMonth);

        return input -> {
            Long memberId = input.memberId();

            if (input.workSchedule() == null) {
                log.warn("WorkSchedule 없음, skip: memberId={}", memberId);
                return null;
            }
            if (input.payrollPolicy() == null) {
                log.error("PayrollPolicy 미등록, skip: memberId={}", memberId);
                throw new BatchException(BatchErrorCode.PAYROLL_POLICY_NOT_FOUND);
            }

            int hourlyWage = input.workSchedule().hourlyWage();
            int dependents = input.payrollPolicy().dependents();
            long nonTaxableMealAllowance = input.payrollPolicy().nonTaxableMealAllowance();
            List<WorkRecordProjection> records = input.workRecords();

            MonthlyPayroll payroll = MonthlyPayroll.builder()
                    .memberId(memberId)
                    .year(ym.getYear())
                    .month(ym.getMonthValue())
                    .build();

            // 주차별 연장근로 누계 (ISO 주차 기준) + 월간 분 합산
            Map<Integer, Double> weeklyOvertimeMap = new HashMap<>();
            int totalRegularMinutes = 0;
            int totalOvertimeMinutes = 0;
            int totalNightMinutes = 0;
            int totalHolidayMinutes = 0;
            int totalHolidayOvertimeMinutes = 0;

            for (WorkRecordProjection record : records) {
                totalRegularMinutes += record.regularMinutes();
                totalOvertimeMinutes += record.overtimeMinutes();
                totalNightMinutes += record.nightMinutes();
                totalHolidayMinutes += record.holidayMinutes();
                totalHolidayOvertimeMinutes += record.holidayOvertimeMinutes();

                // ── 주 52시간 한도 누계 ───────────────────────────────────────
                // 근로기준법 제53조 + 2018년 개정: 1주 = 휴일 포함 7일
                // 연장 한도 사용량 = 평일연장 + 휴일기본 + 휴일연장 (휴일근로 전체 포함)
                double dailyOvertimeHours = (record.overtimeMinutes()
                        + record.holidayMinutes()
                        + record.holidayOvertimeMinutes()) / 60.0;
                int isoWeek = record.bizDate().get(WeekFields.ISO.weekOfWeekBasedYear());
                weeklyOvertimeMap.merge(isoWeek, dailyOvertimeHours, Double::sum);
            }

            // WorkRecord가 원천 데이터로 존재하므로 일별 중간 결과를 행으로 저장하지 않고
            // 월 전체 합산값으로 타입별 PayrollItem을 최대 5건만 생성
            var allItems = wageCalculator.calculate(
                    totalRegularMinutes, totalOvertimeMinutes, totalNightMinutes,
                    totalHolidayMinutes, totalHolidayOvertimeMinutes,
                    hourlyWage, payroll.getId()
            );

            // ── 주 52시간 한도 체크 ───────────────────────────────────────────
            double maxWeeklyOvertime = weeklyOvertimeMap.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0.0);
            boolean overtimeLimitExceeded = maxWeeklyOvertime > WEEKLY_OVERTIME_LIMIT;

            if (overtimeLimitExceeded) {
                log.warn("주 52시간 한도 초과: memberId={}, {}년 {}월, 최대 주간연장={}h (한도 {}h)",
                        memberId, ym.getYear(), ym.getMonthValue(),
                        String.format("%.1f", maxWeeklyOvertime), WEEKLY_OVERTIME_LIMIT);
                weeklyOvertimeMap.forEach((week, hours) -> {
                    if (hours > WEEKLY_OVERTIME_LIMIT) {
                        log.warn("  └ {}주차: 연장 {}h (초과분 {}h)",
                                week,
                                String.format("%.1f", hours),
                                String.format("%.1f", hours - WEEKLY_OVERTIME_LIMIT));
                    }
                });
            }

            payroll.updateOvertimeCheck(overtimeLimitExceeded, maxWeeklyOvertime);

            // ── 총 지급액 ─────────────────────────────────────────────────────
            BigDecimal total = allItems.stream()
                    .map(item -> item.amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            payroll.updateTotalAmount(total.longValue());

            // ── 4대보험 + 세금 공제 ───────────────────────────────────────────
            BigDecimal taxableIncome = total.subtract(BigDecimal.valueOf(nonTaxableMealAllowance))
                    .max(BigDecimal.ZERO);

            InsuranceDeductionResult insurance = insuranceCalculator.calculate(taxableIncome);
            TaxDeductionResult tax = taxCalculator.calculate(taxableIncome, dependents, ym.atDay(1));

            payroll.updateDeductions(
                    insurance.nationalPension().longValue(),
                    insurance.healthInsurance().longValue(),
                    insurance.longTermCare().longValue(),
                    insurance.employmentInsurance().longValue(),
                    tax.incomeTax().longValue(),
                    tax.localIncomeTax().longValue()
            );

            payroll.setPendingItems(allItems);
            return payroll;
        };
    }

    @Bean
    public ItemWriter<MonthlyPayroll> payrollWriter() {
        return chunk -> {
            int itemCount = chunk.getItems().size();
            long beforeMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
            heapLog.info("[HEAP] WRITER_IN    items={} heap_mb={}", itemCount, beforeMb);

            for (MonthlyPayroll payroll : chunk.getItems()) {
                // 동일 (memberId, year, month) 레코드가 이미 존재하면 삭제 후 재삽입 (재실행 멱등성)
                // projection으로 id만 조회 → @Modifying DELETE로 PC를 거치지 않아 flush() 불필요
                monthlyPayrollRepository
                        .findProjectionByMemberIdAndYearAndMonth(payroll.getMemberId(), payroll.getYear(), payroll.getMonth())
                        .ifPresent(existing -> {
                            payrollItemRepository.deleteAllByMonthlyPayrollId(existing.id());
                            monthlyPayrollRepository.deleteByPayrollId(existing.id());
                        });

                monthlyPayrollRepository.batchInsert(List.of(payroll));
                payrollItemRepository.batchInsert(payroll.getPendingItems());
                log.info("급여 저장 완료: memberId={}, {}년 {}월, 총지급={}원, 총공제={}원, 실수령={}원, 주52h초과={}",
                        payroll.getMemberId(), payroll.getYear(), payroll.getMonth(),
                        payroll.getTotalAmount(), payroll.getTotalDeduction(), payroll.getNetPay(),
                        payroll.isOvertimeLimitExceeded() ? "⚠ 초과" : "정상");
            }

            long afterMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
            heapLog.info("[HEAP] WRITER_OUT   items={} heap_mb={}", itemCount, afterMb);
        };
    }
}
