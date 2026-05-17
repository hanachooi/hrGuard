package dev.batch.payroll.step;

import dev.batch.common.exception.BatchException;
import dev.batch.common.exception.BatchSystemErrorCode;
import dev.batch.payroll.dto.PayrollInputDto;
import dev.batch.payroll.exception.PayrollBatchErrorCode;
import dev.batch.payroll.listener.PayrollChunkListener;
import dev.batch.payroll.listener.PayrollRetryListener;
import dev.batch.payroll.listener.PayrollSkipListener;
import dev.common.configuration.DataSourceConfig;
import dev.common.configuration.TransactionManagerConfig;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.repository.MonthlyPayrollRepository;
import dev.payroll.repository.PayrollItemRepository;
import dev.payroll.repository.projection.PayrollItemProjection;
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
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
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

    /**
     * 근로기준법 제53조: 주 연장근로 최대 12시간
     */
    private static final double WEEKLY_OVERTIME_LIMIT = 12.0;

    @Value("${batch.payroll.retry.limit:3}")
    private int retryLimit;

    @Value("${batch.payroll.retry.backoff.initial-ms:1000}")
    private long retryBackoffInitialMs;

    @Value("${batch.payroll.retry.backoff.multiplier:2.0}")
    private double retryBackoffMultiplier;

    @Value("${batch.payroll.retry.backoff.max-ms:30000}")
    private long retryBackoffMaxMs;

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
    private final PayrollRetryListener payrollRetryListener;
    private final PayrollBatchSkipPolicy payrollBatchSkipPolicy;
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
        // Retry 는 chunk-level 이 아니라 writer 내부 stateless RetryTemplate 으로 처리한다.
        // 이유: Spring Batch 의 chunk-level retry + skip 조합은 retry cache key (chunk items 의 hashCode)
        //       가 트랜잭션 사이에 변형되면 TerminatedRetryException 으로 step 이 깨진다.
        //       Writer 내부 stateless retry 는 이 매커니즘과 격리되어 안전하다.
        // 정책: retry 소진 시 BatchException(RETRY_EXHAUSTED=STOP) 으로 escalate → step FAILED.
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
                .skipPolicy(payrollBatchSkipPolicy)               // SKIP / STOP 분기만 담당 (RETRY 는 writer 내부)
                .listener(payrollSkipListener)
                .build();
    }

    /**
     * 일시적 DB 오류(deadlock 등) 재시도 간 back-off.
     *
     * <p>Exponential backoff: initial → initial × multiplier 씩 증가, max 까지 상한.
     * 동일 청크 충돌이 누적될수록 대기시간을 늘려 락 경합을 해소한다.
     * 기본값(1s → 2s → 4s, max 30s)은 application.yml 의 batch.payroll.retry.backoff.* 로 조정.</p>
     */
    @Bean
    public BackOffPolicy payrollRetryBackOffPolicy() {
        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(retryBackoffInitialMs);
        policy.setMultiplier(retryBackoffMultiplier);
        policy.setMaxInterval(retryBackoffMaxMs);
        return policy;
    }

    /**
     * Writer 내부 retry 용 stateless RetryTemplate.
     *
     * <p>Spring Batch 의 chunk-level stateful retry 와 달리, 이 템플릿은 cache 를 쓰지 않아
     * 같은 chunk 의 item hashCode 가 변형되어도 안전하다 (TerminatedRetryException 회피).</p>
     *
     * <p><b>정책</b></p>
     * <ul>
     *   <li>Retry 대상: {@link TransientDataAccessException} (DB lock/deadlock/query timeout),
     *       {@link SocketTimeoutException} (일시적 네트워크 단절)</li>
     *   <li>최대 시도: {@code batch.payroll.retry.limit} (기본 3)</li>
     *   <li>Back-off: {@link #payrollRetryBackOffPolicy()}</li>
     *   <li>각 시도/소진 로깅: {@link PayrollRetryListener}</li>
     * </ul>
     */
    @Bean
    public RetryTemplate payrollWriterRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(payrollRetryBackOffPolicy());
        Map<Class<? extends Throwable>, Boolean> retryable = Map.of(
                TransientDataAccessException.class, true,
                SocketTimeoutException.class, true);
        template.setRetryPolicy(new SimpleRetryPolicy(retryLimit, retryable));
        template.registerListener(payrollRetryListener);
        return template;
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

                int yearValue  = ym.getYear();
                int monthValue = ym.getMonthValue();
                for (Long memberId : memberIds) {
                    buffer.add(new PayrollInputDto(
                            memberId,
                            yearValue,
                            monthValue,
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
                throw new BatchException(PayrollBatchErrorCode.PAYROLL_POLICY_NOT_FOUND);
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

    /**
     * Plan A: chunk 단위 bulk DELETE + INSERT.
     * chunk당 결정론적 4~5쿼리 (멤버별 N+1 제거):
     * 1) SELECT id ... WHERE year=? AND month=? AND member_id IN (...)        — 기존 id 일괄 조회
     * 2) DELETE FROM payroll_item WHERE monthly_payroll_id IN (...)            — 자식 일괄 삭제 (existing 있을 때만)
     * 3) DELETE FROM monthly_payroll WHERE id IN (...)                         — 부모 일괄 삭제 (existing 있을 때만)
     * 4) INSERT INTO monthly_payroll ... (jdbcTemplate.batchUpdate)            — 부모 bulk insert
     * 5) INSERT INTO payroll_item ... (jdbcTemplate.batchUpdate)               — 자식 bulk insert
     * year/month 는 jobParameter 로 chunk 내 동일하므로 첫 item 기준으로 추출.
     *
     * <p><b>Retry 흐름</b> — {@link #payrollWriterRetryTemplate()} 으로 감싸 일시적 DB/네트워크
     * 오류를 stateless 하게 재시도한다. 한도 소진 시 마지막 예외를
     * {@link BatchException}({@link BatchSystemErrorCode#RETRY_EXHAUSTED}=STOP) 으로 wrap 해
     * 던지면 SkipPolicy 의 STOP 분기에서 step FAILED 로 종료된다.</p>
     */
    @Bean
    public ItemWriter<MonthlyPayroll> payrollWriter(RetryTemplate payrollWriterRetryTemplate) {
        return chunk -> {
            try {
                payrollWriterRetryTemplate.execute((RetryCallback<Void, Exception>) ctx -> {
                    doWriteChunk(chunk.getItems());
                    return null;
                });
            } catch (TransientDataAccessException | SocketTimeoutException e) {
                // retry 소진 — STOP 으로 escalate
                throw new BatchException(BatchSystemErrorCode.RETRY_EXHAUSTED, e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                // RetryCallback 시그니처상 checked 도 가능. 실제 발생 가능성은 없지만 안전망.
                throw new BatchException(BatchSystemErrorCode.UNEXPECTED_ERROR, e);
            }
        };
    }

    private void doWriteChunk(List<? extends MonthlyPayroll> items) {
        int itemCount = items.size();
        long beforeMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
        heapLog.info("[HEAP] WRITER_IN    items={} heap_mb={}", itemCount, beforeMb);

        if (items.isEmpty()) {
            return;
        }

        long t0 = System.nanoTime();

        int year = items.get(0).getYear();
        int month = items.get(0).getMonth();
        List<Long> memberIds = items.stream()
                .map(MonthlyPayroll::getMemberId)
                .toList();

        long tSelectStart = System.nanoTime();
        List<Long> existingIds = monthlyPayrollRepository
                .findIdsByYearAndMonthAndMemberIdIn(year, month, memberIds);
        long selectMs = (System.nanoTime() - tSelectStart) / 1_000_000;

        long deleteMs = 0;
        if (!existingIds.isEmpty()) {
            long tDeleteStart = System.nanoTime();
            payrollItemRepository.deleteByMonthlyPayrollIdIn(existingIds);
            monthlyPayrollRepository.deleteByIdIn(existingIds);
            deleteMs = (System.nanoTime() - tDeleteStart) / 1_000_000;
        }

        List<MonthlyPayroll> payrolls = new ArrayList<>(items);
        long tInsertParentStart = System.nanoTime();
        monthlyPayrollRepository.batchInsert(payrolls);
        long insertParentMs = (System.nanoTime() - tInsertParentStart) / 1_000_000;

        List<PayrollItemProjection> allItems = payrolls.stream()
                .flatMap(p -> p.getPendingItems().stream())
                .toList();
        long insertItemMs = 0;
        if (!allItems.isEmpty()) {
            long tInsertItemStart = System.nanoTime();
            payrollItemRepository.batchInsert(allItems);
            insertItemMs = (System.nanoTime() - tInsertItemStart) / 1_000_000;
        }

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("[Writer] chunk={}건 {}년{}월 총={}ms (select={}ms, delete={}ms[existing={}건], insertPayroll={}ms, insertItem={}ms[items={}건])",
                itemCount, year, month, totalMs,
                selectMs, deleteMs, existingIds.size(),
                insertParentMs, insertItemMs, allItems.size());

        long afterMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
        heapLog.info("[HEAP] WRITER_OUT   items={} heap_mb={}", itemCount, afterMb);
    }
}
