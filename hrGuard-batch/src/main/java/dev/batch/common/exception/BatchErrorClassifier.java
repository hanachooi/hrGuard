package dev.batch.common.exception;

import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.dao.TransientDataAccessException;

/**
 * 배치 내 모든 예외를 단일 진입점에서 {@link BatchErrorType}으로 분류한다.
 *
 * <p>SkipPolicy / SkipListener / JobExecutionListener 가 공통으로 사용하여
 * 분류 로직 중복을 제거한다. 외부 알람·DLT 적재·Exit Code 결정 모두
 * 본 분류 결과({@link Classification})를 기반으로 분기한다.</p>
 *
 * <h3>분류 우선순위</h3>
 * <ol>
 *   <li>도메인 예외 ({@link BatchException} + {@link BatchErrorCode}) — 명시 코드/타입 그대로 사용</li>
 *   <li>{@link TransientDataAccessException} → {@link BatchErrorType#RETRY}</li>
 *   <li>{@link OutOfMemoryError} / DataAccessException / Connect 계열 / SkipLimitExceeded → {@link BatchErrorType#STOP}</li>
 *   <li>그 외 → {@link BatchSystemErrorCode#UNEXPECTED_ERROR}</li>
 * </ol>
 */
public final class BatchErrorClassifier {

    private BatchErrorClassifier() {}

    public record Classification(BatchErrorType type, String code, String message, Throwable cause) {
        public boolean is(BatchErrorType t) { return type == t; }
    }

    public static Classification classify(Throwable t) {
        Throwable root = unwrapRoot(t);

        // 1. 도메인 예외 — 명시 분류
        if (root instanceof BatchException be) {
            BatchErrorCode ec = be.getCommonError();
            return new Classification(ec.getType(), ec.getCode(), ec.getMessage(), root);
        }

        // 2. RETRY — 일시적 데이터 접근 오류
        if (root instanceof TransientDataAccessException) {
            return of(BatchSystemErrorCode.DATABASE_TRANSIENT_ERROR, root);
        }

        // 2-2. RETRY — 일시적 네트워크 오류 (소켓 read timeout / 통신 도중 단절)
        //   ConnectException(연결 수립 자체 실패)은 보통 영구 다운이므로 STOP 으로 별도 분류한다.
        if (isClass(root, "java.net.SocketTimeoutException")
                || isClass(root, "com.mysql.cj.exceptions.CommunicationsException")
                || isClass(root, "com.mysql.cj.jdbc.exceptions.CommunicationsException")) {
            return of(BatchSystemErrorCode.NETWORK_TRANSIENT_ERROR, root);
        }

        // 3. STOP — 치명적 인프라/시스템 오류
        if (root instanceof OutOfMemoryError) {
            return of(BatchSystemErrorCode.OUT_OF_MEMORY, root);
        }
        if (root instanceof SkipLimitExceededException) {
            return of(BatchSystemErrorCode.SKIP_LIMIT_EXCEEDED, root);
        }
        if (isClass(root, "org.springframework.security.access.AccessDeniedException")) {
            return of(BatchSystemErrorCode.ACCESS_DENIED, root);
        }
        if (isClass(root, "java.net.ConnectException")) {
            return of(BatchSystemErrorCode.INFRASTRUCTURE_UNAVAILABLE, root);
        }
        if (isDataAccessException(root)) {
            return of(BatchSystemErrorCode.DATABASE_ERROR, root);
        }

        // 4. UNEXPECTED
        return of(BatchSystemErrorCode.UNEXPECTED_ERROR, root);
    }

    // ── 내부 유틸 ────────────────────────────────────────────────────────────

    private static Classification of(BatchErrorCode code, Throwable cause) {
        return new Classification(code.getType(), code.getCode(), code.getMessage(), cause);
    }

    /** 순환 참조 방어를 위해 최대 10단계까지 cause 체인을 탐색한다. */
    private static Throwable unwrapRoot(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current.getCause() != null && current.getCause() != current && depth < 10) {
            current = current.getCause();
            depth++;
        }
        return current;
    }

    private static boolean isClass(Throwable t, String fqcn) {
        Class<?> clazz = t.getClass();
        while (clazz != null) {
            if (fqcn.equals(clazz.getName())) return true;
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private static boolean isDataAccessException(Throwable t) {
        return isClass(t, "org.springframework.dao.DataAccessException");
    }
}
