package dev.batch.payroll.step;

import dev.batch.common.exception.BatchErrorCode;
import dev.batch.common.exception.BatchException;
import dev.batch.payroll.dto.PayrollInputDto;
import dev.batch.payroll.listener.PayrollChunkListener;
import dev.batch.payroll.listener.PayrollSkipListener;
import dev.common.configuration.DataSourceConfig;
import dev.common.configuration.TransactionManagerConfig;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import dev.payroll.repository.MonthlyPayrollRepository;
import dev.payroll.repository.PayrollItemRepository;
import dev.payroll.service.*;
import dev.payrollpolicy.repository.projection.PayrollPolicyProjection;
import dev.workrecord.repository.projection.WorkRecordProjection;
import dev.workschedule.repository.projection.WorkScheduleProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
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
    private final MonthlyPayrollRepository monthlyPayrollRepository;
    private final PayrollItemRepository payrollItemRepository;
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
            ItemStreamReader<PayrollInputDto> payrollReader,
            ItemProcessor<PayrollInputDto, MonthlyPayroll> payrollProcessor,
            ItemWriter<MonthlyPayroll> payrollWriter
    ) {
        return new StepBuilder("payrollStep", jobRepository)
                .<PayrollInputDto, MonthlyPayroll>chunk(CHUNK_SIZE, domainTransactionManager)
                .reader(payrollReader)
                .processor(payrollProcessor)
                .writer(payrollWriter)
                .listener((ChunkListener) payrollChunkListener)
                .listener((StepExecutionListener) payrollChunkListener)
                .listener((ItemReadListener<PayrollInputDto>) payrollChunkListener)
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
     * cursor 기반 JOIN 1쿼리 + member_id 경계 그루핑 reader.
     * - work_record ⟕ work_schedule ⟕ payroll_policy JOIN 결과를 ORDER BY member_id, biz_date 로 스트리밍
     * - peeked 필드로 경계 행을 버퍼링해 다음 read() 에서 재사용 (peek 없이 동일 효과)
     * - 서버사이드 커서: JDBC URL 의 useCursorFetch=true + 양수 fetchSize 두 조건 모두 필요
     *   (fetchSize=Integer.MIN_VALUE 스트리밍 hack 은 1행씩 push 라 네트워크 RTT 누적으로 느림)
     */
    @Bean
    @StepScope
    public ItemStreamReader<PayrollInputDto> payrollReader(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) throws Exception {
        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        String sql = """
                SELECT wr.member_id,
                       wr.biz_date,
                       wr.regular_minutes,
                       wr.overtime_minutes,
                       wr.night_minutes,
                       wr.holiday_minutes,
                       wr.holiday_overtime_minutes,
                       ws.hourly_wage,
                       pp.dependents,
                       pp.non_taxable_meal_allowance
                FROM work_record wr
                LEFT JOIN work_schedule ws ON ws.member_id = wr.member_id
                LEFT JOIN payroll_policy pp ON pp.member_id = wr.member_id
                WHERE wr.biz_date BETWEEN ? AND ?
                ORDER BY wr.member_id ASC, wr.biz_date ASC
                """;

        JdbcCursorItemReader<PayrollRowResult> cursorReader = new JdbcCursorItemReaderBuilder<PayrollRowResult>()
                .name("payrollCursorReader")
                .dataSource(domainDataSource)
                .sql(sql)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, from);
                    ps.setObject(2, to);
                })
                .rowMapper((rs, rowNum) -> new PayrollRowResult(
                        rs.getLong("member_id"),
                        rs.getObject("biz_date", LocalDate.class),
                        rs.getInt("regular_minutes"),
                        rs.getInt("overtime_minutes"),
                        rs.getInt("night_minutes"),
                        rs.getInt("holiday_minutes"),
                        rs.getInt("holiday_overtime_minutes"),
                        (Integer) rs.getObject("hourly_wage"),
                        (Integer) rs.getObject("dependents"),
                        (Long) rs.getObject("non_taxable_meal_allowance")
                ))
                // useCursorFetch=true (JDBC URL) + 양수 fetchSize → 진짜 서버사이드 커서
                // (Integer.MIN_VALUE 스트리밍 hack 아님 — 1행씩 푸시로 인한 네트워크 누적 회피)
                .fetchSize(CHUNK_SIZE)
                .saveState(false)
                .build();
        cursorReader.afterPropertiesSet();

        log.info("급여 계산 Reader 초기화 (Cursor+JOIN): yearMonth={}, range=[{}~{}]", yearMonth, from, to);

        AtomicInteger memberCount = new AtomicInteger();

        return new ItemStreamReader<>() {
            private PayrollRowResult peeked = null;

            @Override
            public void open(ExecutionContext executionContext) throws ItemStreamException {
                cursorReader.open(executionContext);
            }

            @Override
            public void update(ExecutionContext executionContext) throws ItemStreamException {
                cursorReader.update(executionContext);
            }

            @Override
            public void close() throws ItemStreamException {
                cursorReader.close();
            }

            @Override
            public PayrollInputDto read() throws Exception {
                PayrollRowResult first = (peeked != null) ? peeked : cursorReader.read();
                peeked = null;
                if (first == null) {
                    log.info("[Reader] 커서 소진 — 누적 PayrollInputDto 생성 수={}", memberCount.get());
                    return null;
                }

                Long currentMemberId = first.memberId();
                List<WorkRecordProjection> records = new ArrayList<>();
                records.add(toProjection(first));

                PayrollRowResult next;
                while ((next = cursorReader.read()) != null) {
                    if (next.memberId().equals(currentMemberId)) {
                        records.add(toProjection(next));
                    } else {
                        peeked = next;
                        break;
                    }
                }

                WorkScheduleProjection schedule = first.hourlyWage() != null
                        ? new WorkScheduleProjection(currentMemberId, first.hourlyWage()) : null;
                PayrollPolicyProjection policy = (first.dependents() != null && first.nonTaxableMealAllowance() != null)
                        ? new PayrollPolicyProjection(currentMemberId, first.dependents(), first.nonTaxableMealAllowance()) : null;

                int total = memberCount.incrementAndGet();
                if (total % CHUNK_SIZE == 0) {
                    long heapMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
                    heapLog.info("[HEAP] READER_END   total={} heap_mb={}", total, heapMb);
                }

                return new PayrollInputDto(currentMemberId, schedule, policy, records);
            }

            private WorkRecordProjection toProjection(PayrollRowResult row) {
                return new WorkRecordProjection(
                        row.memberId(), row.bizDate(),
                        row.regularMinutes(), row.overtimeMinutes(), row.nightMinutes(),
                        row.holidayMinutes(), row.holidayOvertimeMinutes()
                );
            }
        };
    }

    private record PayrollRowResult(
            Long memberId,
            LocalDate bizDate,
            int regularMinutes,
            int overtimeMinutes,
            int nightMinutes,
            int holidayMinutes,
            int holidayOvertimeMinutes,
            Integer hourlyWage,
            Integer dependents,
            Long nonTaxableMealAllowance
    ) {
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
            List<PayrollItem> allItems = wageCalculator.calculate(
                    totalRegularMinutes, totalOvertimeMinutes, totalNightMinutes,
                    totalHolidayMinutes, totalHolidayOvertimeMinutes,
                    hourlyWage, payroll
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
                    .map(PayrollItem::getAmount)
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
                monthlyPayrollRepository
                        .findByMemberIdAndYearAndMonth(payroll.getMemberId(), payroll.getYear(), payroll.getMonth())
                        .ifPresent(existing -> {
                            payrollItemRepository.deleteAllByMonthlyPayrollId(existing.getId());
                            monthlyPayrollRepository.delete(existing);
                            monthlyPayrollRepository.flush();
                        });

                monthlyPayrollRepository.save(payroll);
                payrollItemRepository.saveAll(payroll.getPendingItems());
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
