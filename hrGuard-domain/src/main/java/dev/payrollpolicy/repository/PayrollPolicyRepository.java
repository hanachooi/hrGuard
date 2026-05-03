package dev.payrollpolicy.repository;

import dev.payrollpolicy.entity.PayrollPolicy;
import dev.payrollpolicy.repository.projection.PayrollPolicyProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollPolicyRepository extends JpaRepository<PayrollPolicy, Long> {

    Optional<PayrollPolicy> findByMemberId(Long memberId);

    // 급여 배치 reader: 영속성 우회용 projection 조회 (단건)
    @Query("SELECT new dev.payrollpolicy.repository.projection.PayrollPolicyProjection(p.memberId, p.dependents, p.nonTaxableMealAllowance) " +
            "FROM PayrollPolicy p WHERE p.memberId = :memberId")
    Optional<PayrollPolicyProjection> findProjectionByMemberId(@Param("memberId") Long memberId);

    // 급여 배치 reader: IN절 bulk 조회 (chunk당 1회)
    @Query("SELECT new dev.payrollpolicy.repository.projection.PayrollPolicyProjection(p.memberId, p.dependents, p.nonTaxableMealAllowance) " +
            "FROM PayrollPolicy p WHERE p.memberId IN :memberIds")
    List<PayrollPolicyProjection> findProjectionByMemberIdIn(@Param("memberIds") List<Long> memberIds);
}
