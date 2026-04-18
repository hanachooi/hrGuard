-- insurance_rate
-- CARE employee_rate = 건강보험료(근로자분) 대비 % (6.475% = 건강보험료 × 6.475%)
-- 국민연금은 상한 변경 시점마다 행 추가 (2025-07-01 상한 변경)

INSERT INTO insurance_rate (insurance_type, employee_rate, employer_rate, effective_from, income_min, income_max,
                            created_at, updated_at)
VALUES
    -- 2025-07-01 이전
    ('NATIONAL_PENSION', 4.5000, 4.5000, '2025-01-01', 390000, 6170000, NOW(), NOW()),
    ('HEALTH', 3.5450, 3.5450, '2025-01-01', NULL, NULL, NOW(), NOW()),
    ('CARE', 6.4750, 6.4750, '2025-01-01', NULL, NULL, NOW(), NOW()),
    ('EMPLOYMENT', 0.9000, 0.9000, '2025-01-01', NULL, NULL, NOW(), NOW()),

-- 2025-07-01: 국민연금 기준소득월액 상한만 변경
    ('NATIONAL_PENSION', 4.5000, 4.5000, '2025-07-01', 400000, 6370000, NOW(), NOW()),

-- 2026년
    ('NATIONAL_PENSION', 4.7500, 4.7500, '2026-01-01', 400000, 6370000, NOW(), NOW()),
    ('HEALTH', 3.5950, 3.5950, '2026-01-01', NULL, NULL, NOW(), NOW()),
    ('CARE', 6.5700, 6.5700, '2026-01-01', NULL, NULL, NOW(), NOW()),
    ('EMPLOYMENT', 0.9000, 0.9000, '2026-01-01', NULL, NULL, NOW(), NOW());
