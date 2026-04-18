package dev.payrollpolicy.service;

import dev.payrollpolicy.entity.PayrollPolicy;
import dev.payrollpolicy.exception.PayrollPolicyError;
import dev.payrollpolicy.exception.PayrollPolicyException;
import dev.payrollpolicy.repository.PayrollPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollPolicyService {

    private final PayrollPolicyRepository payrollPolicyRepository;

    @Transactional
    public PayrollPolicy create(Long memberId, int dependents, long nonTaxableMealAllowance) {
        if (payrollPolicyRepository.findByMemberId(memberId).isPresent()) {
            throw new PayrollPolicyException(PayrollPolicyError.PAYROLL_POLICY_ALREADY_EXISTS);
        }
        return payrollPolicyRepository.save(
                PayrollPolicy.builder()
                        .memberId(memberId)
                        .dependents(dependents)
                        .nonTaxableMealAllowance(nonTaxableMealAllowance)
                        .build()
        );
    }

    @Transactional
    public PayrollPolicy update(Long memberId, int dependents, long nonTaxableMealAllowance) {
        PayrollPolicy policy = findByMemberId(memberId);
        policy.update(dependents, nonTaxableMealAllowance);
        return policy;
    }

    @Transactional(readOnly = true)
    public PayrollPolicy findByMemberId(Long memberId) {
        return payrollPolicyRepository.findByMemberId(memberId)
                .orElseThrow(() -> new PayrollPolicyException(PayrollPolicyError.PAYROLL_POLICY_NOT_FOUND));
    }

}
