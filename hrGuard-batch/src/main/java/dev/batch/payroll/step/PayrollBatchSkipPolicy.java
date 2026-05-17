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
 *   <li>{@code RETRY} → [STOP] ERROR 후 false 반환.
 *       step.retry() 가 한도까지 시도했지만 풀리지 않은 케이스. false 를 반환해야
 *       {@code FaultTolerantChunkProcessor.recoveryCallback} 의 {@code !shouldSkip(...)}
 *       분기로 진입, Scan 모드 없이 곧장 {@code ExhaustedRetryException} 으로 step FAILED.
 *       Scan 모드를 건너뛰므로 RetryContextCache key 변형 → TerminatedRetryException 함정을 회피한다.</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollBatchSkipPolicy implements SkipPolicy {

    private final int skipLimit;
    private final int retryLimit;

    public PayrollBatchSkipPolicy(
            @Value("${batch.payroll.skip-limit:100}") int skipLimit,
            @Value("${batch.payroll.retry.limit:3}") int retryLimit) {
        this.skipLimit = skipLimit;
        this.retryLimit = retryLimit;
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
                // chunk-level retry 가 소진되어 여기로 도달.
                // false 반환 → FaultTolerantChunkProcessor.recoveryCallback 의
                // (!shouldSkip) 분기에서 ExhaustedRetryException 으로 종료.
                // Scan 모드 진입 안 함 → cache key 깨짐 회피.
                log.error("[STOP] [{}] retry {}회 소진 → 배치 중단 | cause={}: {}",
                        c.code(), retryLimit,
                        c.cause().getClass().getSimpleName(), c.cause().getMessage());
                yield false;
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
