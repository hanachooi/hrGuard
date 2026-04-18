package dev.payrollpolicy.repository;

import dev.payrollpolicy.entity.PayrollPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollPolicyRepository extends JpaRepository<PayrollPolicy, Long> {

    Optional<PayrollPolicy> findByMemberId(Long memberId);
}
