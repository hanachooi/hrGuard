package dev.batch.WorkRecord.step;

import dev.batch.WorkRecord.listener.*;
import dev.batch.common.exception.BatchException;
import dev.common.configuration.TransactionManagerConfig;
import dev.commute.repository.CommuteRepository;
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.repository.WorkRecordRepository;
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
 *   Processor: WorkRecordSyncProcessor.compute() → List&lt;WorkRecord&gt;
 *              └ 기존 OFFICE 레코드 삭제(idempotency) + 슬롯 계산
 *   Writer   : WorkRecord 배치 저장
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WorkRecordStepConfig {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 50;
    private static final int RETRY_LIMIT = 3;

    private final JobRepository jobRepository;
    private final WorkRecordSyncProcessor workRecordSyncProcessor;
    private final CommuteRepository commuteRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkRecordJobExecutionListener workRecordJobExecutionListener;
    private final WorkRecordChunkListener workRecordChunkListener;
    private final WorkRecordItemReadListener workRecordItemReadListener;
    private final WorkRecordItemProcessListener workRecordItemProcessListener;
    private final WorkRecordItemWriteListener workRecordItemWriteListener;

    @Qualifier(TransactionManagerConfig.DOMAIN_TRANSACTION_MANAGER)
    private final PlatformTransactionManager domainTransactionManager;

    @Bean
    public Step workRecordStep(
            ListItemReader<Long> workRecordReader,
            ItemProcessor<Long, List<WorkRecord>> workRecordProcessor,
            ItemWriter<List<WorkRecord>> workRecordWriter
    ) {
        return new StepBuilder("workRecordStep", jobRepository)
                .<Long, List<WorkRecord>>chunk(CHUNK_SIZE, domainTransactionManager)
                .reader(workRecordReader)
                .processor(workRecordProcessor)
                .writer(workRecordWriter)
                .listener((Object) workRecordJobExecutionListener) // Job + Step 시작/종료
                .listener((Object) workRecordChunkListener)        // Chunk 진행률
                .listener((Object) workRecordItemReadListener)     // Read 단계
                .listener((Object) workRecordItemProcessListener)  // Process 단계
                .listener((Object) workRecordItemWriteListener)    // Write 단계
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
     */
    @Bean
    @StepScope
    public ListItemReader<Long> workRecordReader(
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate date = LocalDate.parse(targetDate);
        List<Long> memberIds = commuteRepository.findMemberIdsWithCompletedCommuteByDate(date);
        log.info("[WorkRecord] 처리 대상 인원: {}명 ({})", memberIds.size(), date);
        return new ListItemReader<>(memberIds);
    }

    /**
     * memberId → List&lt;WorkRecord&gt; 변환.
     * idempotency 처리와 슬롯 계산은 WorkRecordSyncProcessor가 담당한다.
     */
    @Bean
    @StepScope
    public ItemProcessor<Long, List<WorkRecord>> workRecordProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return memberId -> {
            LocalDate date = LocalDate.parse(targetDate);
            List<WorkRecord> slots = workRecordSyncProcessor.compute(memberId, date);
            if (slots.isEmpty()) {
                log.debug("[WorkRecord] 생성 슬롯 없음 skip: memberId={}", memberId);
                return null;
            }
            return slots;
        };
    }

    /**
     * List&lt;WorkRecord&gt; 배치 저장.
     */
    @Bean
    public ItemWriter<List<WorkRecord>> workRecordWriter() {
        return chunk -> {
            List<WorkRecord> all = chunk.getItems().stream()
                    .flatMap(List::stream)
                    .toList();
            workRecordRepository.saveAll(all);
            log.debug("[WorkRecord] WorkRecord {} 건 저장", all.size());
        };
    }
}
