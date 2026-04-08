package dev.batch.payroll.listener;

import dev.batch.common.exception.BatchException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

/**
 * Skip 발생 시 원인을 분류하여 로그로 기록하는 리스너.
 *
 * <p>Spring Batch의 fault-tolerant 정책에 의해 skip된 아이템을
 * Reader / Processor / Writer 단계별로 감지합니다.</p>
 *
 * <h3>skip 발생 가능 케이스</h3>
 * <ul>
 *   <li>Processor — WorkSchedule 미등록</li>
 *   <li>Processor — 퇴근 시각 미기록 (이 경우 continue 로 처리되므로 skip 카운트 미포함)</li>
 *   <li>Writer    — DB 저장 실패 (재시도 후에도 실패 시)</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollSkipListener implements SkipListener<Long, Object> {

    private final Counter skipReadCounter;
    private final Counter skipProcessCounter;
    private final Counter skipWriteCounter;

    public PayrollSkipListener(MeterRegistry meterRegistry) {
        this.skipReadCounter = Counter.builder("payroll.batch.skip")
                .tag("phase", "read")
                .description("Reader 단계 skip 횟수")
                .register(meterRegistry);
        this.skipProcessCounter = Counter.builder("payroll.batch.skip")
                .tag("phase", "process")
                .description("Processor 단계 skip 횟수")
                .register(meterRegistry);
        this.skipWriteCounter = Counter.builder("payroll.batch.skip")
                .tag("phase", "write")
                .description("Writer 단계 skip 횟수")
                .register(meterRegistry);
    }

    // ── Reader skip ──────────────────────────────────────────────────────────

    @Override
    public void onSkipInRead(Throwable t) {
        skipReadCounter.increment();
        log.warn("[SKIP][READ] 읽기 중 skip 발생 | cause={}: {}",
                t.getClass().getSimpleName(), t.getMessage());
    }

    // ── Processor skip ───────────────────────────────────────────────────────

    @Override
    public void onSkipInProcess(Long memberId, Throwable t) {
        skipProcessCounter.increment();

        if (t instanceof BatchException batchEx) {
            log.warn("[SKIP][PROCESS] memberId={} | [{}] {}",
                    memberId,
                    batchEx.getCommonError().getCode(),
                    batchEx.getCommonError().getMessage());
        } else {
            log.warn("[SKIP][PROCESS] memberId={} | cause={}: {}",
                    memberId, t.getClass().getSimpleName(), t.getMessage());
        }
    }

    // ── Writer skip ──────────────────────────────────────────────────────────

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        skipWriteCounter.increment();
        log.warn("[SKIP][WRITE] item={} | cause={}: {}",
                item, t.getClass().getSimpleName(), t.getMessage(), t);
    }
}
