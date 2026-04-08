package dev.batch.common.exception;

import dev.common.exception.ServiceException;

/**
 * 배치 전용 비즈니스 예외.
 *
 * <p>API 모듈의 도메인별 예외({@code MemberException} 등)에 대응합니다.
 * {@link ServiceException}을 상속하므로 공통 예외 처리 흐름에서도 동작합니다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 단순 에러 코드만
 * throw new BatchException(BatchErrorCode.WORK_CONTRACT_NOT_FOUND);
 *
 * // 근본 원인(cause) 포함 — OOM·DB 오류 래핑 시
 * throw new BatchException(BatchErrorCode.OUT_OF_MEMORY, oomError);
 * }</pre>
 */
public class BatchException extends ServiceException {

    public BatchException(BatchErrorCode errorCode) {
        super(errorCode);
    }

    public BatchException(BatchErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    @Override
    public BatchErrorCode getCommonError() {
        return (BatchErrorCode) super.getCommonError();
    }
}
