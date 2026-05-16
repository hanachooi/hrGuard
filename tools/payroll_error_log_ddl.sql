-- payroll_error_log: 급여 배치에서 Skip된 항목 추적 테이블 (Dead Letter Table)
-- autohrm DB에 생성
--
-- 보정 후 재배치를 위해 error_type / error_code / original_data(JSON) 를 함께 저장한다.
--   error_type    : RETRY / SKIP / STOP  (STOP 은 적재 안 됨 — 배치 자체가 중단되어 의미 없음)
--   error_code    : BatchErrorCode 의 코드 (e.g. PAYROLL_BATCH_SKIP_404_2, COMMON_BATCH_RETRY_500_1)
--   original_data : Skip된 원본 입력 데이터 JSON (PayrollInputDto / MonthlyPayroll)
CREATE TABLE IF NOT EXISTS payroll_error_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    member_id     BIGINT,                  -- READ 단계 skip은 NULL 가능
    `year`        INT,
    `month`       INT,
    phase         VARCHAR(10)  NOT NULL,   -- READ / PROCESS / WRITE
    error_type    VARCHAR(8)   NOT NULL,   -- RETRY / SKIP
    error_code    VARCHAR(40)  NOT NULL,   -- e.g. PAYROLL_BATCH_SKIP_404_2
    error_message TEXT,
    original_data JSON,                    -- skip된 원본 입력 데이터 (재처리용)
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_member    (member_id),
    INDEX idx_ym        (`year`, `month`),
    INDEX idx_type_code (error_type, error_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
