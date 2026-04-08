package dev.batch.WorkRecord.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

/**
 * WorkRecordSync Chunk 단위 진행률 리스너.
 *
 * <p>청크 커밋/롤백마다 진행 상황을 로그로 남기고 Micrometer 카운터를 증가시킵니다.</p>
 *
 * <p>노출 메트릭:</p>
 * <pre>
 *   workrecord_batch_chunks_total{status="success"}
 *   workrecord_batch_chunks_total{status="error"}
 *   workrecord_batch_items_read_total
 *   workrecord_batch_items_written_total
 *   workrecord_batch_items_filtered_total
 * </pre>
 */
@Slf4j
@Component
public class WorkRecordChunkListener implements ChunkListener {

    private final Counter chunkSuccessCounter;
    private final Counter chunkErrorCounter;
    private final Counter itemsReadCounter;
    private final Counter itemsWrittenCounter;
    private final Counter itemsFilteredCounter;

    public WorkRecordChunkListener(MeterRegistry registry) {
        this.chunkSuccessCounter = Counter.builder("workrecord.batch.chunks")
                .tag("status", "success")
                .register(registry);
        this.chunkErrorCounter = Counter.builder("workrecord.batch.chunks")
                .tag("status", "error")
                .register(registry);
        this.itemsReadCounter = Counter.builder("workrecord.batch.items.read")
                .register(registry);
        this.itemsWrittenCounter = Counter.builder("workrecord.batch.items.written")
                .register(registry);
        this.itemsFilteredCounter = Counter.builder("workrecord.batch.items.filtered")
                .register(registry);
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        StepExecution se = context.getStepContext().getStepExecution();
        log.debug("[WorkRecordSync Chunk 시작] 커밋={}, 읽기={}, 쓰기={}, 필터={}",
                se.getCommitCount(), se.getReadCount(), se.getWriteCount(), se.getFilterCount());
    }

    @Override
    public void afterChunk(ChunkContext context) {
        StepExecution se = context.getStepContext().getStepExecution();

        long prevRead = getLong(se, "wr.prevRead");
        long prevWritten = getLong(se, "wr.prevWritten");
        long prevFilter = getLong(se, "wr.prevFiltered");

        long deltaRead = se.getReadCount() - prevRead;
        long deltaWritten = se.getWriteCount() - prevWritten;
        long deltaFilter = se.getFilterCount() - prevFilter;

        itemsReadCounter.increment(deltaRead);
        itemsWrittenCounter.increment(deltaWritten);
        itemsFilteredCounter.increment(deltaFilter);
        chunkSuccessCounter.increment();

        se.getExecutionContext().putLong("wr.prevRead", se.getReadCount());
        se.getExecutionContext().putLong("wr.prevWritten", se.getWriteCount());
        se.getExecutionContext().putLong("wr.prevFiltered", se.getFilterCount());

        log.info("[WorkRecordSync Chunk #{} 완료] 읽기={} | 저장={} | skip={} | 롤백={}",
                se.getCommitCount(),
                se.getReadCount(), se.getWriteCount(),
                se.getFilterCount(), se.getRollbackCount());
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        StepExecution se = context.getStepContext().getStepExecution();
        Throwable error = (Throwable) context.getAttribute(ChunkListener.ROLLBACK_EXCEPTION_KEY);
        chunkErrorCounter.increment();
        log.error("[WorkRecordSync Chunk 롤백] 읽기={} | 누적롤백={} | 원인={}",
                se.getReadCount(), se.getRollbackCount(),
                error != null ? error.getMessage() : "unknown");
    }

    private long getLong(StepExecution se, String key) {
        Object val = se.getExecutionContext().get(key);
        return val instanceof Long l ? l : 0L;
    }
}
