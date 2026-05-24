package dev.batch.payroll.listener;

import dev.batch.common.exception.BatchErrorClassifier;
import dev.batch.common.exception.BatchErrorType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Spring Batch chunk-level retry 의 재시도 라이프사이클을 로깅한다.
 *
 * <p>{@code StepBuilder.faultTolerant().retry(...).retryLimit(...).listener(this)} 로 등록되어
 * 매 시도({@link #onError})와 한도 소진({@link #close})을 통일된 라벨로 남긴다.
 * 한도 소진 후에는 {@link dev.batch.payroll.step.PayrollBatchSkipPolicy} 의 RETRY 분기가
 * false 를 반환해 {@code FaultTolerantChunkProcessor} 가 곧장 {@code ExhaustedRetryException}
 * 으로 step FAILED 시킨다.</p>
 *
 * <p><b>주의 — onError 는 SKIP/STOP 예외에도 fire 된다.</b>
 * FaultTolerantChunkProcessor 의 단일 RetryTemplate 이 retry/skip 인프라를 공유하므로
 * SKIP 분류 예외 (예: BatchException(PAYROLL_POLICY_NOT_FOUND)) 도 retry template 을
 * 거치며 onError 를 발화시킨다. 분류 결과 type=RETRY 일 때만 [RETRY] 로깅하도록
 * 필터링 — 그 외엔 SkipListener / SkipPolicy 가 단일 진입점이라 침묵.</p>
 */
@Slf4j
@Component
public class PayrollRetryListener implements RetryListener {

    private final Counter retryCounter;

    public PayrollRetryListener(MeterRegistry meterRegistry) {
        this.retryCounter = Counter.builder("payroll.batch.retry")
                .description("청크 레벨 재시도 누적 횟수")
                .register(meterRegistry);
    }

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        return true;
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // type=RETRY 인 예외만 [RETRY] 로깅 — SKIP/STOP 은 SkipListener/SkipPolicy 가 단일 로깅.
        if (BatchErrorClassifier.classify(throwable).type() != BatchErrorType.RETRY) {
            return;
        }
        retryCounter.increment();
        try (var ignored1 = MDC.putCloseable("log_tag", "RETRY");
             var ignored2 = MDC.putCloseable("attempt", String.valueOf(context.getRetryCount()))) {
            log.warn("payrollJob retry | error={}: {}",
                    throwable.getClass().getSimpleName(), throwable.getMessage());
        }
    }

    // close() 는 default no-op.
    // Spring Batch 의 chunk-level retry 는 stateful — RetryTemplate.execute() 가 chunk attempt 마다
    // 호출되어 매 호출의 finally 에서 close() 가 fire 된다. 따라서 close() 만으로는 "한도 소진" 을
    // 판별할 수 없다. 진짜 소진 시점의 단일 진입점은 PayrollBatchSkipPolicy 의 RETRY 분기
    // ([STOP] retry N회 소진).
}
