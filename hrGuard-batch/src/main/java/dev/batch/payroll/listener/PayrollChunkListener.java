package dev.batch.payroll.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chunk 단위 처리 진행률 리스너.
 *
 * <p>Spring Batch의 chunk 지향 스텝은 내부적으로 RepeatStatus를 사용해
 * Reader → Processor → Writer 루프를 반복합니다.
 * Reader가 null을 반환하는 순간 RepeatStatus.FINISHED로 전환되며 스텝이 종료됩니다.
 * 이 리스너는 각 반복(= 트랜잭션 1회)이 끝날 때마다 로그를 남기고
 * Micrometer Counter를 증가시켜 Prometheus → Grafana로 실시간 노출합니다.</p>
 *
 * <p>노출되는 메트릭 (Prometheus 형식):</p>
 * <pre>
 *   payroll_batch_chunks_total{status="success"}   - 커밋 성공 청크 수
 *   payroll_batch_chunks_total{status="error"}     - 롤백 청크 수
 *   payroll_batch_items_read_total                 - 읽은 아이템 누적 수
 *   payroll_batch_items_written_total              - 저장 완료 아이템 누적 수
 *   payroll_batch_items_filtered_total             - 필터(skip)된 아이템 누적 수
 * </pre>
 */
@Slf4j
@Component
public class PayrollChunkListener implements ChunkListener, StepExecutionListener {

    private static final Logger heapLog = LoggerFactory.getLogger("dev.batch.heap");

    // ── Micrometer Counter 정의 ──────────────────────────────────────────────
    // Counter는 단조 증가(monotonically increasing)만 가능 → Prometheus rate() 함수와 궁합이 좋음
    // Grafana에서 increase(payroll_batch_items_written_total[배치실행시간]) 으로 한 번 실행의 총량을 볼 수 있음
    private final Counter chunkSuccessCounter;
    private final Counter chunkErrorCounter;
    private final Counter itemsReadCounter;
    private final Counter itemsWrittenCounter;
    private final Counter itemsFilteredCounter;
    private final Counter commuteSkippedCounter;

    /**
     * Step 실행 단위로 퇴근 미기록 건수를 추적 (Singleton이므로 beforeStep에서 리셋)
     */
    private final AtomicLong commuteSkippedCount = new AtomicLong(0);

    /**
     * 청크 단위 heap 추적용 — 직전 chunk afterChunk 시점의 heap (MB)
     * 톱니 패턴 검증: chunk 시작 → heap 증가 → write/commit 후 참조 해제 → GC 시점에 회수
     */
    private final AtomicLong prevAfterChunkHeapMb = new AtomicLong(-1);
    private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();

    public PayrollChunkListener(MeterRegistry registry) {
        this.chunkSuccessCounter = Counter.builder("payroll.batch.chunks")
                .tag("status", "success")
                .description("커밋에 성공한 청크 수")
                .register(registry);

        this.chunkErrorCounter = Counter.builder("payroll.batch.chunks")
                .tag("status", "error")
                .description("롤백된 청크 수")
                .register(registry);

        this.itemsReadCounter = Counter.builder("payroll.batch.items.read")
                .description("Reader가 읽어들인 누적 아이템 수")
                .register(registry);

        this.itemsWrittenCounter = Counter.builder("payroll.batch.items.written")
                .description("Writer가 저장 완료한 누적 아이템 수 (정산 성공)")
                .register(registry);

        this.itemsFilteredCounter = Counter.builder("payroll.batch.items.filtered")
                .description("Processor가 null 반환해 필터된 누적 아이템 수 (계약 없음 등)")
                .register(registry);

        this.commuteSkippedCounter = Counter.builder("payroll.batch.commute.skipped")
                .description("퇴근 미기록으로 정산에서 제외된 출퇴근 건수")
                .register(registry);
    }

    // ── StepExecutionListener ────────────────────────────────────────────────

    /**
     * Step 시작 시 퇴근 미기록 카운터 리셋 (Singleton 재사용 대비)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        commuteSkippedCount.set(0);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    // ── 퇴근 미기록 카운터 (Processor에서 호출) ──────────────────────────────

    public void incrementCommuteSkipped() {
        commuteSkippedCounter.increment();
        commuteSkippedCount.incrementAndGet();
    }

    public long getCommuteSkippedCount() {
        return commuteSkippedCount.get();
    }

    /**
     * 청크 처리 시작 전 호출.
     * 직전 청크까지의 누적 카운터를 DEBUG로 남기고, chunk 진입 시점 heap 을 INFO 로 남깁니다.
     * <p>이 값과 afterChunk 의 heap 값을 비교해 톱니 패턴(누적 → 회수)을 관찰합니다.</p>
     */
    @Override
    public void beforeChunk(ChunkContext context) {
        StepExecution se = context.getStepContext().getStepExecution();
        long heapMb = usedHeapMb();
        long prev = prevAfterChunkHeapMb.get();
        long delta = prev < 0 ? 0 : heapMb - prev;

        log.info("[Chunk #{} 시작] heap={}MB (직전 afterChunk 대비 {}{}MB) — DTO 누적 시작",
                se.getCommitCount() + 1, heapMb,
                delta >= 0 ? "+" : "", delta);
        heapLog.info("[HEAP] CHUNK_BEFORE chunk={} heap_mb={}", se.getCommitCount() + 1, heapMb);

        log.debug("[Chunk 시작] 커밋={}, 읽기={}, 쓰기={}, 필터={}, 롤백={}",
                se.getCommitCount(), se.getReadCount(),
                se.getWriteCount(), se.getFilterCount(), se.getRollbackCount());
    }

    /**
     * 청크 처리 완료(커밋) 후 호출.
     *
     * <p>StepExecution 카운터는 '배치 시작부터 현재까지의 누적값'이므로
     * 이전 afterChunk에서 기록한 값과의 차이(delta)를 Counter에 더합니다.
     * 이렇게 해야 Prometheus에서 rate()로 정확한 처리 속도를 측정할 수 있습니다.</p>
     */
    @Override
    public void afterChunk(ChunkContext context) {
        StepExecution se = context.getStepContext().getStepExecution();

        // StepExecution 카운터는 누적값 → 청크 1개의 delta를 계산해서 Counter에 추가
        // commitCount가 N이면 이번 청크에서 처음으로 N번째 커밋이 발생한 것
        long chunkRead = se.getReadCount() - (long) getOrDefault(context, "prevRead", 0L);
        long chunkWritten = se.getWriteCount() - (long) getOrDefault(context, "prevWritten", 0L);
        long chunkFiltered = se.getFilterCount() - (long) getOrDefault(context, "prevFiltered", 0L);

        itemsReadCounter.increment(chunkRead);
        itemsWrittenCounter.increment(chunkWritten);
        itemsFilteredCounter.increment(chunkFiltered);
        chunkSuccessCounter.increment();

        // 다음 청크의 delta 계산을 위해 현재 누적값을 저장
        context.getStepContext().getStepExecution()
                .getExecutionContext().putLong("prevRead", se.getReadCount());
        context.getStepContext().getStepExecution()
                .getExecutionContext().putLong("prevWritten", se.getWriteCount());
        context.getStepContext().getStepExecution()
                .getExecutionContext().putLong("prevFiltered", se.getFilterCount());

        long total = se.getWriteCount() + se.getFilterCount();
        String rate = total > 0
                ? String.format("%.1f", (double) se.getWriteCount() / total * 100.0)
                : "0.0";

        long heapMb = usedHeapMb();
        prevAfterChunkHeapMb.set(heapMb);

        heapLog.info("[HEAP] CHUNK_AFTER  chunk={} read={} written={} heap_mb={}",
                se.getCommitCount(), se.getReadCount(), se.getWriteCount(), heapMb);

        log.info("[Chunk #{} 완료] 읽기={} | 저장={} | 필터(skip)={} | 롤백={} | 정산성공률={}% | heap={}MB",
                se.getCommitCount(),
                se.getReadCount(),
                se.getWriteCount(),
                se.getFilterCount(),
                se.getRollbackCount(),
                rate,
                heapMb);

        // chunk 버퍼(inputs/outputs) 참조 해제 시점 — 다음 chunk 진입 전까지 GC 대상
        log.info("[Chunk #{}] write/commit 완료 → chunk 버퍼의 PayrollInputDto {}개 + MonthlyPayroll {}개 참조 해제 (GC 대상)",
                se.getCommitCount(), chunkRead, chunkWritten + chunkFiltered);
    }

    private long usedHeapMb() {
        return memoryMxBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    /**
     * 청크 처리 중 예외 발생(롤백) 후 호출.
     */
    @Override
    public void afterChunkError(ChunkContext context) {
        StepExecution se = context.getStepContext().getStepExecution();
        Throwable error = (Throwable) context.getAttribute(ChunkListener.ROLLBACK_EXCEPTION_KEY);

        chunkErrorCounter.increment();

        log.error("[Chunk #{} 롤백] 읽기={} | 저장={} | 누적롤백={} | 원인={}",
                se.getCommitCount() + 1,
                se.getReadCount(),
                se.getWriteCount(),
                se.getRollbackCount(),
                error != null ? error.getMessage() : "unknown");
    }

    private Object getOrDefault(ChunkContext context, String key, Object defaultValue) {
        Object value = context.getStepContext().getStepExecution()
                .getExecutionContext().get(key);
        return value != null ? value : defaultValue;
    }
}
