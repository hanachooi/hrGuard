package dev.batch.payroll.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Writer 내부 stateless {@link org.springframework.retry.support.RetryTemplate} 의
 * 재시도 라이프사이클을 로깅한다.
 *
 * <p>Spring Batch chunk-level retry 가 아니라 writer 안에서 직접 호출되는 RetryTemplate 에
 * 등록되어, 매 시도와 한도 소진을 통일된 라벨로 남긴다. retry 가 소진되면 writer 가
 * {@code BatchException(RETRY_EXHAUSTED)} 로 wrap 해 던지고, SkipPolicy 의 STOP 분기에서
 * step FAILED 로 종료된다.</p>
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
        log.error("[RETRY_EXHAUSTED] payrollJob | attempt={} | error={}: {} | STOP 으로 escalate",
                context.getRetryCount(),
                throwable.getClass().getSimpleName(), throwable.getMessage());
    }
}
