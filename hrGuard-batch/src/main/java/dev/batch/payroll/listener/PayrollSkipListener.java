package dev.batch.payroll.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.batch.common.exception.BatchErrorClassifier;
import dev.batch.common.exception.BatchErrorClassifier.Classification;
import dev.batch.common.exception.BatchErrorType;
import dev.batch.payroll.dto.PayrollInputDto;
import dev.payroll.entity.MonthlyPayroll;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

/**
 * Skip 발생 시 원인을 분류하여 로그와 Dead Letter Table(DLT)에 기록하는 리스너.
 *
 * <p>{@link BatchErrorClassifier} 분류 결과({@link BatchErrorType})에 따라 DLT 적재 여부를 결정한다.
 * 실제 INSERT 는 {@link PayrollErrorLogWriter}(REQUIRES_NEW)에 위임하여 chunk 트랜잭션과 격리한다.</p>
 *
 * <h3>저장 정책</h3>
 * <ul>
 *   <li>{@link BatchErrorType#SKIP}  → 저장 (보정 후 재배치 기반). 실제로 SkipListener 가 호출되는 유일한 케이스.</li>
 *   <li>{@link BatchErrorType#RETRY} → 도달 불가 — {@code PayrollBatchSkipPolicy} 의 RETRY 분기가 {@code false}
 *       를 반환해 step FAILED 로 직행하므로 skip 이벤트 자체가 발생하지 않는다.</li>
 *   <li>{@link BatchErrorType#STOP}  → 도달 불가 — SkipPolicy 가 {@code false} 반환.
 *       (방어 차원으로 {@code saveErrorLog} 내부에서 한 번 더 STOP 가드)</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollSkipListener implements SkipListener<PayrollInputDto, MonthlyPayroll> {

    private final Counter skipReadCounter;
    private final Counter skipProcessCounter;
    private final Counter skipWriteCounter;
    private final ObjectMapper objectMapper;
    private final PayrollErrorLogWriter errorLogWriter;

    public PayrollSkipListener(
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            PayrollErrorLogWriter errorLogWriter) {
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
        this.objectMapper = objectMapper;
        this.errorLogWriter = errorLogWriter;
    }

    // ── Reader skip ──────────────────────────────────────────────────────────

    @Override
    public void onSkipInRead(Throwable t) {
        skipReadCounter.increment();
        Classification c = BatchErrorClassifier.classify(t);
        log.warn("[SKIP][READ] [{}] {} | cause={}: {}",
                c.code(), c.message(),
                c.cause().getClass().getSimpleName(), c.cause().getMessage());
        saveErrorLog(null, null, null, "READ", c, null);
    }

    // ── Processor skip ───────────────────────────────────────────────────────

    @Override
    public void onSkipInProcess(PayrollInputDto item, Throwable t) {
        skipProcessCounter.increment();
        Classification c = BatchErrorClassifier.classify(t);
        long memberId = item.memberId();
        int year      = item.year();
        int month     = item.month();

        log.warn("[SKIP][PROCESS] memberId={} | {}년{}월 | [{}] {}",
                memberId, year, month, c.code(), c.message());
        saveErrorLog(memberId, year, month, "PROCESS", c, toJson(item));
    }

    // ── Writer skip ──────────────────────────────────────────────────────────

    @Override
    public void onSkipInWrite(MonthlyPayroll item, Throwable t) {
        skipWriteCounter.increment();
        Classification c = BatchErrorClassifier.classify(t);
        long memberId = item.getMemberId();
        int year      = item.getYear();
        int month     = item.getMonth();

        log.warn("[SKIP][WRITE] memberId={} | {}년{}월 | [{}] {} | cause={}: {}",
                memberId, year, month, c.code(), c.message(),
                c.cause().getClass().getSimpleName(), c.cause().getMessage(), t);
        saveErrorLog(memberId, year, month, "WRITE", c, toJson(item));
    }

    // ── DLT 저장 위임 ────────────────────────────────────────────────────────

    private void saveErrorLog(Long memberId, Integer year, Integer month,
                              String phase, Classification c, String originalDataJson) {
        // STOP 은 DLT 미적재 (배치 자체가 중단되어 재처리 의미 없음)
        if (c.type() == BatchErrorType.STOP) return;
        errorLogWriter.save(memberId, year, month, phase, c, originalDataJson);
    }

    /** 원본 데이터를 JSON 으로 직렬화. 실패 시 fallback. */
    private String toJson(Object item) {
        if (item == null) return null;
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            log.warn("[SKIP] 원본 데이터 JSON 직렬화 실패 — type={} cause={}",
                    item.getClass().getSimpleName(), e.getMessage());
            return "{\"_serializationError\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
