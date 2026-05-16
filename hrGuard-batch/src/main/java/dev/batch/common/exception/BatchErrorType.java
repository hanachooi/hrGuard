package dev.batch.common.exception;

/**
 * 배치 에러 유형 — 후속 조치를 결정하는 분류축.
 *
 * <ul>
 *   <li>{@link #RETRY} — 일시적 오류 (DB 데드락, 네트워크 순단 등). 재시도로 회복 가능.</li>
 *   <li>{@link #SKIP}  — 확정적 오류 (데이터 누락/형식 오류 등). 해당 레코드만 건너뛰고 DLT 기록.</li>
 *   <li>{@link #STOP}  — 치명적 오류 (DB 다운, 권한 없음, 계산 시스템 결함 등). 즉시 Job 중단(Exit 1).</li>
 * </ul>
 */
public enum BatchErrorType {
    RETRY,
    SKIP,
    STOP
}
