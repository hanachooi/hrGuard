package dev.payroll.repository;

import dev.payroll.entity.MonthlyPayroll;

import java.util.List;

public interface MonthlyPayrollRepositoryCustom {

    void batchInsert(List<MonthlyPayroll> payrolls);

    /**
     * (member_id, year, month) UNIQUE 충돌 시 기존 row 의 변경 가능 컬럼만 갱신.
     * id, created_at 은 갱신 대상에서 제외하여 자식 FK 정합성 유지 (호출 측에서 in-memory id 를 기존 id 로 미리 맞춰둔다).
     */
    void bulkUpsert(List<MonthlyPayroll> payrolls);
}
