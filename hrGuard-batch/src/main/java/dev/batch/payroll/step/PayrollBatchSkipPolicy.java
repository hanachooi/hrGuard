package dev.batch.payroll.step;

import dev.batch.common.exception.BatchErrorClassifier;
import dev.batch.common.exception.BatchErrorClassifier.Classification;
import dev.batch.common.exception.BatchSystemErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.stereotype.Component;

/**
 * 급여 정산 배치 Skip 정책.
 *
 * <p>{@link BatchErrorClassifier}에 분류를 위임하고, {@code BatchErrorType}별로 처리 방식만 결정한다.
 * 분류 로직 자체는 {@link BatchErrorClassifier}에 단일 진입점으로 통합되어 있다.</p>
 *
 * <h3>판단 규칙</h3>
 * <ul>
 *   <li>{@code STOP}             → [SKIP_REJECT] ERROR 로그 후 즉시 배치 중단 (skipCount 무관)</li>
 *   <li>{@code SKIP} / {@code RETRY} → skip 한도 체크 후 [SKIP] WARN 로그 + skip
 *       <br>(RETRY는 재시도 소진 후 본 정책으로 도달 — SKIP과 동일하게 처리하되 코드는 RETRY 코드 유지)</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollBatchSkipPolicy implements SkipPolicy {

    static final int SKIP_LIMIT = 100;

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        Classification c = BatchErrorClassifier.classify(t);

        return switch (c.type()) {
            case STOP -> {
                log.error("[SKIP_REJECT] [{}] {} → 배치 중단 | cause={}: {}",
                        c.code(), c.message(),
                        c.cause().getClass().getSimpleName(), c.cause().getMessage(), t);
                yield false;
            }
            case SKIP, RETRY -> {
                checkSkipLimit(skipCount, t);
                log.warn("[SKIP] [{}] {} (skip #{}) | cause={}",
                        c.code(), c.message(), skipCount + 1, c.cause().getMessage());
                yield true;
            }
        };
    }

    private void checkSkipLimit(long skipCount, Throwable t) {
        if (skipCount >= SKIP_LIMIT) {
            log.error("[SKIP_REJECT] [{}] 한도 {}건 초과 → Job 강제 종료 | cause={}: {}",
                    BatchSystemErrorCode.SKIP_LIMIT_EXCEEDED.getCode(),
                    SKIP_LIMIT, t.getClass().getSimpleName(), t.getMessage());
            throw new SkipLimitExceededException(SKIP_LIMIT, t);
        }
    }
}
