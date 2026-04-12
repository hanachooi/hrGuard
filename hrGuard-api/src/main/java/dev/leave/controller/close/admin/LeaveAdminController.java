package dev.leave.controller.close.admin;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.leave.service.LeaveService;
import dev.leave.service.request.RejectRequest;
import dev.leave.service.response.AnnualLeaveBalanceResponse;
import dev.leave.service.response.AnnualLeaveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/annual-leaves")
@RequiredArgsConstructor
public class LeaveAdminController {

    private final LeaveService leaveService;

    /**
     * 대기 중인 휴가 목록
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<AnnualLeaveResponse>>> findPending(
            @AuthMemberId Long memberId
    ) {
        List<AnnualLeaveResponse> responses = leaveService.findPending()
                .stream()
                .map(AnnualLeaveResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 휴가 승인 — 승인 시 WorkRecord(ANNUAL_LEAVE) 슬롯 자동 점유
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @AuthMemberId Long memberId,
            @PathVariable Long id
    ) {
        leaveService.approve(id);
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 휴가 반려
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthMemberId Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {
        leaveService.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 연차 추가 부여 (HR 전용)
     */
    @PutMapping("/grant")
    public ResponseEntity<ApiResponse<AnnualLeaveBalanceResponse>> grant(
            @AuthMemberId Long memberId,
            @RequestParam Long targetMemberId,
            @RequestParam int year,
            @RequestParam double days
    ) {
        var balance = leaveService.grant(targetMemberId, year, days);
        return ResponseEntity.ok(ApiResponse.success(AnnualLeaveBalanceResponse.from(balance)));
    }
}
