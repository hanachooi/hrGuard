package dev.batch.sync.step;

import dev.batch.common.exception.BatchException;
import dev.common.configuration.TransactionManagerConfig;
import dev.commute.repository.CommuteRepository;
import dev.payroll.constant.WorkType;
import dev.payroll.entity.WorkRecord;
import dev.payroll.repository.WorkRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Step;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Commute → WorkRecord(OFFICE) 동기화 Step.
 *
 * <h3>처리 흐름</h3>
 * <pre>
 *   Reader   : targetDate에 퇴근 완료한 memberId 목록 (ListItemReader)
 *   Processor: CommuteSyncProcessor.compute() → List&lt;WorkRecord&gt;
 *              └ 기존 OFFICE 레코드 삭제(idempotency) 후 빈 슬롯 계산
 *   Writer   : WorkRecord 배치 저장
 * </pre>
 *
 * <h3>idempotency 전략</h3>
 * Processor에서 해당 멤버·날짜의 OFFICE 레코드를 먼저 DELETE한 뒤 재생성한다.
 * 승인 기반(FIELD, BUSINESS_TRIP, ANNUAL_LEAVE) 레코드는 workType 조건으로 보호된다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WorkRecordSyncStepConfig {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 50;
    private static final int RETRY_LIMIT = 3;

    private final JobRepository jobRepository;
    private final CommuteSyncProcessor commuteSyncProcessor;
    private final CommuteRepository commuteRepository;
    private final WorkRecordRepository workRecordRepository;

    @Qualifier(TransactionManagerConfig.DOMAIN_TRANSACTION_MANAGER)
    private final PlatformTransactionManager domainTransactionManager;

    @Bean
    public Step workRecordSyncStep(
            ListItemReader<Long> workRecordSyncReader,
            ItemProcessor<Long, List<WorkRecord>> workRecordSyncProcessor,
            ItemWriter<List<WorkRecord>> workRecordSyncWriter
    ) {
        return new StepBuilder("workRecordSyncStep", jobRepository)
                .<Long, List<WorkRecord>>chunk(CHUNK_SIZE, domainTransactionManager)
                .reader(workRecordSyncReader)
                .processor(workRecordSyncProcessor)
                .writer(workRecordSyncWriter)
                .faultTolerant()
                .skip(BatchException.class)
                .skipLimit(SKIP_LIMIT)
                .noSkip(Error.class)
                .retry(TransientDataAccessException.class)
                .retryLimit(RETRY_LIMIT)
                .build();
    }

    /**
     * targetDate에 퇴근 완료(outTime != null)된 사원 목록을 읽는다.
     * 퇴근 미기록 사원은 이 단계에서 제외되므로 Processor에서 별도 체크 불필요.
     */
    @Bean("workRecordSyncReader")
    @StepScope
    public ListItemReader<Long> workRecordSyncReader(
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate date = LocalDate.parse(targetDate);
        List<Long> memberIds = commuteRepository.findMemberIdsWithCompletedCommuteByDate(date);
        log.info("[WorkRecordSync] 처리 대상 인원: {}명 ({})", memberIds.size(), date);
        return new ListItemReader<>(memberIds);
    }

    /**
     * memberId → List&lt;WorkRecord&gt; 변환.
     *
     * <ol>
     *   <li>기존 OFFICE WorkRecord 삭제 (idempotency)</li>
     *   <li>CommuteSyncProcessor.compute() 로 빈 슬롯 계산</li>
     *   <li>슬롯이 없으면 null 반환 → Spring Batch가 filter로 처리</li>
     * </ol>
     */
    @Bean("workRecordSyncProcessor")
    @StepScope
    public ItemProcessor<Long, List<WorkRecord>> workRecordSyncProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return memberId -> {
            LocalDate date = LocalDate.parse(targetDate);

            // idempotency: 이전 실행에서 만들어진 OFFICE 레코드 제거
            workRecordRepository.deleteByMemberIdAndBizDateAndWorkType(
                    memberId, date, WorkType.OFFICE);

            List<WorkRecord> slots = commuteSyncProcessor.compute(memberId, date);

            if (slots.isEmpty()) {
                log.debug("[WorkRecordSync] 생성 슬롯 없음 skip: memberId={}", memberId);
                return null; // filterCount 증가
            }
            return slots;
        };
    }

    /**
     * List&lt;WorkRecord&gt; 배치 저장.
     * chunk.getItems()는 List&lt;List&lt;WorkRecord&gt;&gt; 이므로 flatMap 후 saveAll.
     */
    @Bean("workRecordSyncWriter")
    public ItemWriter<List<WorkRecord>> workRecordSyncWriter() {
        return chunk -> {
            List<WorkRecord> all = chunk.getItems().stream()
                    .flatMap(List::stream)
                    .toList();
            workRecordRepository.saveAll(all);
            log.debug("[WorkRecordSync] WorkRecord {} 건 저장", all.size());
        };
    }
}
