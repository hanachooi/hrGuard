package dev.batch.payroll.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Chunk-oriented step 의 재시도 라이프사이클을 로깅한다.
 *
 * <p>Spring Batch chunk retry 는 stateless 에 가까워 {@code RetryContext.STATE_KEY}
 * 로 특정 item 을 식별할 수 없다 (거의 항상 null). 따라서 chunk 단위 시도 횟수와
 * 마지막 예외만 기록한다. 개별 item 식별은 {@link PayrollSkipListener} 에서 처리.</p>
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

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable == null) return;
        log.error("[RETRY_EXHAUSTED] payrollJob | attempt={} | error={}: {} | skip 정책으로 위임",
                context.getRetryCount(),
                throwable.getClass().getSimpleName(), throwable.getMessage());
    }
}
