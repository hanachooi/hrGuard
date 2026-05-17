package dev.batch.payroll.exception;

import dev.batch.common.exception.BatchErrorCode;
import dev.batch.common.exception.BatchErrorType;
import dev.common.exception.HttpStatusCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 급여 정산 배치 도메인 에러 코드.
 *
 * <h3>코드 체계</h3>
 * {@code PAYROLL_BATCH_{TYPE}_{HTTP}_{SEQ}} — TYPE ∈ {RETRY, SKIP, STOP}
 *
 * <h3>처리 전략</h3>
 * {@link BatchErrorType}에 따라 {@code PayrollBatchSkipPolicy}가 자동 분기한다.
 * <ul>
 *   <li>{@link BatchErrorType#SKIP}  → 해당 건 skip + DLT 기록</li>
 *   <li>{@link BatchErrorType#STOP}  → 즉시 배치 중단(Step FAILED)</li>
 *   <li>{@link BatchErrorType#RETRY} → 일시적 오류 (도메인 코드는 보통 사용 안 함, 공통 코드에 정의)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PayrollBatchErrorCode implements BatchErrorCode {

    // ── SKIP: 데이터 누락 — 해당 건만 건너뛰고 DLT 기록 ──────────────────────

    WORK_SCHEDULE_NOT_FOUND(
            HttpStatusCode.NOT_FOUND,
            "PAYROLL_BATCH_SKIP_404_1",
            "근무 스케줄이 등록되지 않았습니다.",
            BatchErrorType.SKIP),

    PAYROLL_POLICY_NOT_FOUND(
            HttpStatusCode.NOT_FOUND,
            "PAYROLL_BATCH_SKIP_404_2",
            "급여 정산 정책이 등록되지 않았습니다. 정산 전 PayrollPolicy를 등록해 주세요.",
            BatchErrorType.SKIP),

    WORK_RECORD_EMPTY(
            HttpStatusCode.NOT_FOUND,
            "PAYROLL_BATCH_SKIP_404_3",
            "해당 기간의 근무 기록이 존재하지 않습니다.",
            BatchErrorType.SKIP),

    // ── STOP: 계산 시스템 오류 — 코드/설정 결함, 즉시 배치 중단 ─────────────

    WAGE_CALCULATION_FAILED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "PAYROLL_BATCH_STOP_500_1",
            "급여 계산 중 오류가 발생했습니다.",
            BatchErrorType.STOP),

    TAX_CALCULATION_FAILED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "PAYROLL_BATCH_STOP_500_2",
            "세금 계산 중 오류가 발생했습니다.",
            BatchErrorType.STOP),

    INSURANCE_CALCULATION_FAILED(
            HttpStatusCode.INTERNAL_SERVER_ERROR,
            "PAYROLL_BATCH_STOP_500_3",
            "4대보험 계산 중 오류가 발생했습니다.",
            BatchErrorType.STOP);

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String message;
    private final BatchErrorType type;
}
