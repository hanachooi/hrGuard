package dev.payroll.repository.projection;

/**
 * 멱등성 삭제용 projection.
 * JPQL 생성자 표현식으로 직접 매핑되어 영속성 컨텍스트를 거치지 않는다.
 */
public record MonthlyPayrollProjection(Long id) {
}
