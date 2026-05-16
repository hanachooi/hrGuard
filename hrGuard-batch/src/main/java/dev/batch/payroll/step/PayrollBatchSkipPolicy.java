package dev.batch.payroll.step;

import dev.batch.common.exception.BatchErrorClassifier;
import dev.batch.common.exception.BatchErrorClassifier.Classification;
import dev.batch.common.exception.BatchSystemErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 급여 정산 배치 Skip 정책.
 *
 * <p>{@link BatchErrorClassifier}에 분류를 위임하고, {@code BatchErrorType}별로 처리 방식만 결정한다.
 * 분류 로직 자체는 {@link BatchErrorClassifier}에 단일 진입점으로 통합되어 있다.</p>
 *
 * <h3>판단 규칙 — 라벨은 "원인 분류({@link dev.batch.common.exception.BatchErrorType})" 기준</h3>
 * <ul>
 *   <li>{@code STOP}  → [STOP] ERROR 후 즉시 배치 중단 (skipCount 무관). skip 한도 초과도 동일.</li>
 *   <li>{@code SKIP}  → [SKIP] WARN 후 한도 안에서 skip. 데이터 자체가 잘못된 케이스.</li>
 *   <li>{@code RETRY} → [SKIP] WARN 후 한도 안에서 skip.
 *       step.retry() 가 한도까지 시도했지만 풀리지 않은 케이스 — 액션은 skip 이므로
 *       라벨도 [SKIP] 으로 통일한다. 분류 코드(RETRY 류)는 그대로 노출되어 DLT 의
 *       error_type='RETRY' 와 1:1 매칭된다. 로그 메시지에 "retry 한도 소진" 부가설명 포함.</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollBatchSkipPolicy implements SkipPolicy {

    private final int skipLimit;

    public PayrollBatchSkipPolicy(
            @Value("${batch.payroll.skip-limit:100}") int skipLimit) {
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        Classification c = BatchErrorClassifier.classify(t);

        return switch (c.type()) {
            case STOP -> {
                log.error("[STOP] [{}] {} → 배치 중단 | cause={}: {}",
                        c.code(), c.message(),
                        c.cause().getClass().getSimpleName(), c.cause().getMessage(), t);
                yield false;
            }
            case SKIP -> {
                checkSkipLimit(skipCount, t);
                log.warn("[SKIP] [{}] {} (skip #{}) | cause={}",
                        c.code(), c.message(), Math.max(skipCount, 0) + 1, c.cause().getMessage());
                yield true;
            }
            case RETRY -> {
                // step.retry() 한도까지 시도했는데도 풀리지 않은 케이스.
                // 액션은 skip 이므로 라벨도 [SKIP] 으로 통일 (retry 시도 자체는 RetryListener 가 [RETRY] 로 로깅).
                // 분류 코드는 RETRY 류 유지 → DLT 의 error_type='RETRY' 와 1:1 매칭.
                // skipCount=-1 인 분류용 호출에도 대비해 음수 보정.
                checkSkipLimit(skipCount, t);
                log.warn("[SKIP] [{}] retry 한도 소진 → skip 처리 (skip #{}) | cause={}",
                        c.code(), Math.max(skipCount, 0) + 1, c.cause().getMessage());
                yield true;
            }
        };
    }

    /** skip 한도 초과는 STOP 의 한 종류로 분류된다. */
    private void checkSkipLimit(long skipCount, Throwable t) {
        if (skipCount >= skipLimit) {
            log.error("[STOP] [{}] 한도 {}건 초과 → Job 강제 종료 | cause={}: {}",
                    BatchSystemErrorCode.SKIP_LIMIT_EXCEEDED.getCode(),
                    skipLimit, t.getClass().getSimpleName(), t.getMessage());
            throw new SkipLimitExceededException(skipLimit, t);
        }
    }
}
