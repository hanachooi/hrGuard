package dev.payroll.repository.projection;

/**
 * UPSERT 전 (memberId → 기존 id) 매핑용 projection.
 */
public record MonthlyPayrollIdMember(Long id, Long memberId) {
}
