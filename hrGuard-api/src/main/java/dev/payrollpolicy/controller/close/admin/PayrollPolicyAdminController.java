package dev.payrollpolicy.controller.close.admin;

import dev.common.dto.ApiResponse;
import dev.payrollpolicy.service.PayrollPolicyService;
import dev.payrollpolicy.service.request.PayrollPolicyUpsertRequest;
import dev.payrollpolicy.service.response.PayrollPolicyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payroll-policies")
public class PayrollPolicyAdminController {

    private final PayrollPolicyService payrollPolicyService;

    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<PayrollPolicyResponse>> getPolicy(
            @PathVariable Long memberId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                PayrollPolicyResponse.from(payrollPolicyService.findByMemberId(memberId))
        ));
    }

    @PostMapping("/{memberId}")
    public ResponseEntity<ApiResponse<PayrollPolicyResponse>> createPolicy(
            @PathVariable Long memberId,
            @Valid @RequestBody PayrollPolicyUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                PayrollPolicyResponse.from(payrollPolicyService.create(
                        memberId,
                        request.dependents(),
                        request.nonTaxableMealAllowance()
                ))
        ));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<ApiResponse<PayrollPolicyResponse>> updatePolicy(
            @PathVariable Long memberId,
            @Valid @RequestBody PayrollPolicyUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                PayrollPolicyResponse.from(payrollPolicyService.update(
                        memberId,
                        request.dependents(),
                        request.nonTaxableMealAllowance()
                ))
        ));
    }
}
