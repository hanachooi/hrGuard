package dev.batch.common.exception;

import dev.common.exception.CommonError;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배치 전용 에러 코드.
 *
 * <p>API 모듈의 {@code ErrorCode}에 대응하며, 배치에서 발생할 수 있는
 * 오류 유형을 망라합니다.</p>
 *
 * <h3>코드 체계</h3>
 * <ul>
 *   <li>BATCH_4xx : 파라미터·데이터 오류 (클라이언트/운영자 실수)</li>
 *   <li>BATCH_5xx : 시스템·인프라 오류 (서버 내부 장애)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum BatchErrorCode implements CommonError {

    // ── 파라미터 / 데이터 오류 (4xx 계열) ─────────────────────────────────

    /**
     * yearMonth 파라미터 누락 또는 형식 오류
     */
    INVALID_JOB_PARAMETER(
            HttpStatusCode.BAD_REQUEST,
            "BATCH_400_1",
            "잘못된 Job 파라미터입니다."),

    /**
     * WorkSchedule 미등록 (1건 skip 처리)
     */
    WORK_SCHEDULE_NOT_FOUND(
            HttpStatusCode.NOT_FOUND,
            "BATCH_404_1",
            "근무 스케줄 정보가 존재하지 않습니다."),

    /**
     * PayrollPolicy 미등록 — 정산 전 반드시 등록 필요 (Job 중단)
     */
    PAYROLL_POLICY_NOT_FOUND(
            HttpStatusCode.NOT_FOUND,
            "BATCH_404_2",
            "급여 정산 정책이 등록되지 않았습니다. 정산 전 PayrollPolicy를 등록해 주세요."),

    /**
     * Commute 퇴근 시각 미기록 (1건 skip 처리)
     */
    COMMUTE_OUT_TIME_MISSING(
            HttpStatusCode.BAD_REQUEST,
            "BATCH_400_2",
            "퇴근 시각이 기록되지 않은 출퇴근 데이터입니다."),

    // ── 시스템 / 인프라 오류 (5xx 계열) ───────────────────────────────────

    /**
     * DB 연결 실패 또는 쿼리 오류
     */
    DATABASE_ERROR(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "BATCH_500_1",
            "데이터베이스 오류가 발생했습니다."),

    /**
     * JVM 힙 고갈 (OutOfMemoryError)
     */
    OUT_OF_MEMORY(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "BATCH_500_2",
            "JVM 메모리가 부족합니다. 청크 크기를 줄이거나 힙 설정을 확인하세요."),

    /**
     * 처리 중 skip 한도 초과
     */
    SKIP_LIMIT_EXCEEDED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "BATCH_500_3",
            "Skip 한도를 초과하여 Job이 중단되었습니다."),

    /**
     * 재시도 한도 초과
     */
    RETRY_EXHAUSTED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "BATCH_500_4",
            "재시도 한도를 초과했습니다."),

    /**
     * 예상치 못한 치명적 오류
     */
    UNEXPECTED_ERROR(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "BATCH_500_9",
            "알 수 없는 오류로 배치가 비정상 종료되었습니다.");

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
}
