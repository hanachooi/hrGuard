package dev.batch.common.exception;

import dev.common.exception.CommonError;

/**
 * 배치 에러 코드 마커 인터페이스.
 *
 * <p>배치 처리 정책 분기를 위한 {@link BatchErrorType}을 갖는 코드만 이 인터페이스를 구현한다.
 * {@code BatchSystemErrorCode}, {@code PayrollBatchErrorCode} 등 enum 들이
 * 모두 본 인터페이스를 구현하므로 {@link BatchErrorClassifier}는 enum 종류와 무관하게
 * 동일한 방식으로 분류 결과를 만들어낼 수 있다.</p>
 *
 * <p>API 모듈의 {@link CommonError}는 HTTP 응답 표준만 정의하므로,
 * 배치 처리 정책(retry/skip/stop) 분기를 위한 정보는 본 인터페이스로 분리한다.</p>
 */
public interface BatchErrorCode extends CommonError {

    BatchErrorType getType();
}
