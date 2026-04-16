package dev.batch.WorkRecord.step;

import dev.batch.WorkRecord.listener.*;
import dev.batch.common.exception.BatchException;
import dev.businesstrip.repository.BusinessTripRepository;
import dev.common.configuration.TransactionManagerConfig;
import dev.commute.repository.CommuteRepository;
import dev.fieldwork.repository.FieldWorkRepository;
import dev.leave.repository.LeaveRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Commute + Leave + BusinessTrip + FieldWork → WorkRecord(일별 집계) 산출 Step.
 *
 * <h3>처리 흐름</h3>
 * <pre>
 *   Reader   : targetDate에 활동이 있는 memberId 목록
 *              (완료된 출퇴근 OR 승인된 휴가/출장/외근)
 *   Processor: WorkRecordComputeProcessor.compute() → WorkRecord 집계 1건
 *              └ 기존 레코드 삭제(idempotency) + 원천별 시간 + 정산용 시간 산출
 *   Writer   : WorkRecord 저장
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WorkRecordStepConfig {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 3;
    private static final int RETRY_LIMIT = 3;

    private final JobRepository jobRepository;
    private final WorkRecordComputeProcessor workRecordComputeProcessor;
    private final CommuteRepository commuteRepository;
    private final LeaveRepository leaveRepository;
    private final BusinessTripRepository businessTripRepository;
    private final FieldWorkRepository fieldWorkRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkRecordJobExecutionListener workRecordJobExecutionListener;
    private final WorkRecordChunkListener workRecordChunkListener;
    private final WorkRecordItemReadListener workRecordItemReadListener;
    private final WorkRecordItemProcessListener workRecordItemProcessListener;
    private final WorkRecordItemWriteListener workRecordItemWriteListener;

    @Qualifier(TransactionManagerConfig.DOMAIN_TRANSACTION_MANAGER)
    private final PlatformTransactionManager domainTransactionManager;

    @Bean
    public Step workRecordComputeStep(
            ListItemReader<Long> workRecordReader,
            ItemProcessor<Long, WorkRecord> workRecordProcessor,
            ItemWriter<WorkRecord> workRecordWriter
    ) {
        return new StepBuilder("workRecordComputeStep", jobRepository)
                .<Long, WorkRecord>chunk(CHUNK_SIZE, domainTransactionManager)
                .reader(workRecordReader)
                .processor(workRecordProcessor)
                .writer(workRecordWriter)
                .listener((Object) workRecordJobExecutionListener)
                .listener((Object) workRecordChunkListener)
                .listener((Object) workRecordItemReadListener)
                .listener((Object) workRecordItemProcessListener)
                .listener((Object) workRecordItemWriteListener)
                .faultTolerant()
                .skip(BatchException.class)
                .skipLimit(SKIP_LIMIT)
                .noSkip(Error.class)
                .retry(TransientDataAccessException.class)
                .retryLimit(RETRY_LIMIT)
                .build();
    }

    /**
     * targetDate에 근태 활동이 있는 memberId 목록을 읽습니다.
     *
     * <p>출퇴근 완료, 승인된 휴가·출장·외근을 모두 포함하므로
     * 외근만 있는 날이나 연차만 있는 날도 정산 대상에 포함됩니다.</p>
     */
    @Bean
    @StepScope
    public ListItemReader<Long> workRecordReader(
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate date = LocalDate.parse(targetDate);
        java.time.LocalDateTime startOfDay = date.atStartOfDay();
        java.time.LocalDateTime nextDay = date.plusDays(1).atStartOfDay();

        Set<Long> memberIds = new LinkedHashSet<>();
        memberIds.addAll(commuteRepository.findMemberIdsWithCompletedCommuteByDate(date));
        memberIds.addAll(leaveRepository.findApprovedMemberIdsByDate(startOfDay, nextDay));
        memberIds.addAll(businessTripRepository.findApprovedMemberIdsByDate(startOfDay, nextDay));
        memberIds.addAll(fieldWorkRepository.findApprovedMemberIdsByWorkDate(startOfDay, nextDay));

        log.info("[WorkRecord] 처리 대상 인원: {}명 ({})", memberIds.size(), date);
        return new ListItemReader<>(List.copyOf(memberIds));
    }

    /**
     * memberId → WorkRecord(집계) 변환.
     * 활동이 없으면 null 반환 → batch가 filter-out.
     */
    @Bean
    @StepScope
    public ItemProcessor<Long, WorkRecord> workRecordProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return memberId -> {
            LocalDate date = LocalDate.parse(targetDate);
            WorkRecord record = workRecordComputeProcessor.compute(memberId, date);
            if (record == null) {
                log.debug("[WorkRecord] 집계 결과 없음 skip: memberId={}", memberId);
            }
            return record;
        };
    }

    /**
     * WorkRecord 저장.
     */
    @Bean
    public ItemWriter<WorkRecord> workRecordWriter() {
        return chunk -> {
            List<? extends WorkRecord> records = chunk.getItems();
            workRecordRepository.saveAll(records);
            log.debug("[WorkRecord] {}건 저장 완료", records.size());
        };
    }
}
