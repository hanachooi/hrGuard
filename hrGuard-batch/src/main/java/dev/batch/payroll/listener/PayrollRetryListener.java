package dev.batch.payroll.listener;

import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Component
public class PayrollRetryListener implements RetryListener {

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        return true;
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("[RETRY] payrollJob | attempt={} | error={}: {}",
                context.getRetryCount(),
                throwable.getClass().getSimpleName(), throwable.getMessage());
    }

    // close() 는 default no-op.
    // Spring Batch 의 chunk-level retry 는 stateful — RetryTemplate.execute() 가 chunk attempt 마다
    // 호출되어 매 호출의 finally 에서 close() 가 fire 된다. 따라서 close() 만으로는 "한도 소진" 을
    // 판별할 수 없다. 진짜 소진 시점의 단일 진입점은 PayrollBatchSkipPolicy 의 RETRY 분기
    // ([STOP] retry N회 소진).
}
