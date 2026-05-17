package dev.batch.common.exception;

import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배치 시스템/인프라 에러 코드 — 도메인에 종속되지 않는 공통 오류.
 *
 * <p>주로 {@code BatchErrorClassifier}가 JVM/DB/Spring 예외를 자동 매핑하는 결과 코드 보관소로 사용한다.
 * 도메인 비즈니스 코드는 {@code dev.batch.{domain}.exception.{Domain}BatchErrorCode}에 분리한다
 * (예: {@code PayrollBatchErrorCode}).</p>
 *
 * <h3>코드 체계</h3>
 * {@code COMMON_BATCH_{TYPE}_{HTTP}_{SEQ}} — TYPE ∈ {RETRY, SKIP, STOP}
 */
@Getter
@RequiredArgsConstructor
public enum BatchSystemErrorCode implements BatchErrorCode {

    // ── RETRY: 일시적 시스템 오류 ────────────────────────────────────────────

    /** DB 데드락/락 획득 실패/타임아웃 등 일시적 데이터 접근 오류 */
    DATABASE_TRANSIENT_ERROR(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_RETRY_500_1",
            "일시적인 데이터베이스 오류입니다. 재시도합니다.",
            BatchErrorType.RETRY),

    /** Socket read timeout / 통신 도중 일시 단절 등 회복 가능한 네트워크 오류 */
    NETWORK_TRANSIENT_ERROR(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_RETRY_500_2",
            "일시적인 네트워크 오류입니다. 재시도합니다.",
            BatchErrorType.RETRY),

    // ── STOP: 치명적 오류 (배치 중단) ────────────────────────────────────────

    /** yearMonth 등 Job 파라미터 누락/형식 오류 */
    INVALID_JOB_PARAMETER(
            HttpStatusCode.BAD_REQUEST,
            "COMMON_BATCH_STOP_400_1",
            "잘못된 Job 파라미터입니다.",
            BatchErrorType.STOP),

    /** 권한 없음 (Spring Security AccessDeniedException 등) */
    ACCESS_DENIED(
            HttpStatusCode.FORBIDDEN,
            "COMMON_BATCH_STOP_403_1",
            "배치 실행 권한이 없습니다.",
            BatchErrorType.STOP),

    /** DB 연결 실패/쿼리 오류 (TransientDataAccessException 외) */
    DATABASE_ERROR(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_STOP_500_1",
            "데이터베이스 오류가 발생했습니다.",
            BatchErrorType.STOP),

    /** JVM 힙 고갈 */
    OUT_OF_MEMORY(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_STOP_500_2",
            "JVM 메모리가 부족합니다. 청크 크기를 줄이거나 힙 설정을 확인하세요.",
            BatchErrorType.STOP),

    /** DB 다운/네트워크 단절 (ConnectException, CommunicationsException 등) */
    INFRASTRUCTURE_UNAVAILABLE(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_STOP_500_3",
            "DB 또는 네트워크 연결이 단절되었습니다.",
            BatchErrorType.STOP),

    /** Skip 한도 초과 — Job 강제 종료 */
    SKIP_LIMIT_EXCEEDED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_STOP_500_4",
            "Skip 한도를 초과하여 Job이 중단되었습니다.",
            BatchErrorType.STOP),

    /** 재시도 한도 초과 후에도 회복되지 않음 */
    RETRY_EXHAUSTED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_STOP_500_5",
            "재시도 한도를 초과했습니다.",
            BatchErrorType.STOP),

    /** 분류되지 않는 치명적 오류 */
    UNEXPECTED_ERROR(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "COMMON_BATCH_STOP_500_9",
            "알 수 없는 오류로 배치가 비정상 종료되었습니다.",
            BatchErrorType.STOP);

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
    private final BatchErrorType type;
}
