package dev.payrollpolicy.controller.close.user;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.payrollpolicy.service.PayrollPolicyService;
import dev.payrollpolicy.service.response.PayrollPolicyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll-policies")
public class PayrollPolicyUserController {

    private final PayrollPolicyService payrollPolicyService;

    @GetMapping
    public ResponseEntity<ApiResponse<PayrollPolicyResponse>> getPolicy(
            @AuthMemberId Long memberId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                PayrollPolicyResponse.from(payrollPolicyService.findByMemberId(memberId))
        ));
    }
}
