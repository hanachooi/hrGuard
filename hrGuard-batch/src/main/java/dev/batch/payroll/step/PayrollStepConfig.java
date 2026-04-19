package dev.batch.payroll.step;

import dev.batch.common.exception.BatchErrorCode;
import dev.batch.common.exception.BatchException;
import dev.batch.payroll.listener.PayrollChunkListener;
import dev.batch.payroll.listener.PayrollSkipListener;
import dev.common.configuration.TransactionManagerConfig;
import dev.payroll.entity.MonthlyPayroll;
import dev.payroll.entity.PayrollItem;
import dev.payroll.repository.MonthlyPayrollRepository;
import dev.payroll.repository.PayrollItemRepository;
import dev.payroll.service.*;
import dev.payrollpolicy.entity.PayrollPolicy;
import dev.payrollpolicy.repository.PayrollPolicyRepository;
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

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PayrollStepConfig {

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
     * memberId 1명의 해당 월 WorkRecord를 읽어 MonthlyPayroll을 생성합니다.
     *
     * <h3>처리 흐름</h3>
     * <pre>
     *   WorkRecord 조회 (이미 분류된 정산용 집계 필드 보유)
     *     → WageCalculator로 PayrollItem 생성
     *     → 주차별 연장누계로 주 52시간 한도 체크
     *     → InsuranceCalculator + TaxCalculator로 공제 계산
     * </pre>
     *
     * <h3>skip 조건</h3>
     * <ul>
     *   <li>WorkSchedule 없음 → null 반환 (멤버 전체 skip)</li>
     * </ul>
     */
    @Bean
    @StepScope
    public ItemProcessor<Long, MonthlyPayroll> payrollProcessor(
            @Value("#{jobParameters['yearMonth']}") String yearMonth
    ) {
        return memberId -> {
            YearMonth ym = YearMonth.parse(yearMonth);

            // ── 시급 조회 ─────────────────────────────────────────────────────
            WorkSchedule schedule = workScheduleRepository.findByMemberId(memberId)
                    .orElse(null);
            if (schedule == null) {
                log.warn("WorkSchedule 없음, skip: memberId={}", memberId);
                return null;
            }
            int hourlyWage = schedule.getHourlyWage();

            // ── 정산 설정 조회 (부양가족 수, 식대 비과세) ─────────────────────
            PayrollPolicy payrollPolicy = payrollPolicyRepository.findByMemberId(memberId)
                    .orElseThrow(() -> {
                        log.error("PayrollPolicy 미등록, Job 중단: memberId={}", memberId);
                        return new BatchException(BatchErrorCode.PAYROLL_POLICY_NOT_FOUND);
                    });
            int dependents = payrollPolicy.getDependents();
            long nonTaxableMealAllowance = payrollPolicy.getNonTaxableMealAllowance();

            // ── WorkRecord 조회 (member + biz_date unique, 1일 1건) ───────────
            List<WorkRecord> records = workRecordRepository
                    .findByMemberIdAndBizDateBetween(memberId, ym.atDay(1), ym.atEndOfMonth());

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

            for (WorkRecord record : records) {
                totalRegularMinutes += record.getRegularMinutes();
                totalOvertimeMinutes += record.getOvertimeMinutes();
                totalNightMinutes += record.getNightMinutes();
                totalHolidayMinutes += record.getHolidayMinutes();
                totalHolidayOvertimeMinutes += record.getHolidayOvertimeMinutes();

                // ── 주 52시간 한도 누계 ───────────────────────────────────────
                // 근로기준법 제53조 + 2018년 개정: 1주 = 휴일 포함 7일
                // 연장 한도 사용량 = 평일연장 + 휴일기본 + 휴일연장 (휴일근로 전체 포함)
                double dailyOvertimeHours = (record.getOvertimeMinutes()
                        + record.getHolidayMinutes()
                        + record.getHolidayOvertimeMinutes()) / 60.0;
                int isoWeek = record.getBizDate().get(WeekFields.ISO.weekOfWeekBasedYear());
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
            for (MonthlyPayroll payroll : chunk.getItems()) {
                // 동일 (memberId, year, month) 레코드가 이미 존재하면 삭제 후 재삽입 (재실행 멱등성)
                monthlyPayrollRepository
                        .findByMemberIdAndYearAndMonth(payroll.getMemberId(), payroll.getYear(), payroll.getMonth())
                        .ifPresent(existing -> {
                            payrollItemRepository.deleteAll(
                                    payrollItemRepository.findByMonthlyPayrollId(existing.getId()));
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
        };
    }
}
