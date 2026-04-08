package dev.attendance.leave.controller;

import dev.attendance.leave.service.AnnualLeaveService;
import dev.attendance.leave.service.request.AnnualLeaveRequest;
import dev.attendance.leave.service.request.RejectRequest;
import dev.attendance.leave.service.response.AnnualLeaveBalanceResponse;
import dev.attendance.leave.service.response.AnnualLeaveResponse;
import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 휴가 신청/승인 API.
 *
 * <pre>
 *   POST   /api/v1/annual-leaves               본인 휴가 신청
 *   GET    /api/v1/annual-leaves/me            내 휴가 목록 조회
 *   GET    /api/v1/annual-leaves/me/balance    내 연차 잔여일수 조회
 *   GET    /api/v1/annual-leaves/pending       대기 중인 휴가 목록 (관리자)
 *   PUT    /api/v1/annual-leaves/{id}/approve  승인 → WorkRecord(ANNUAL_LEAVE) 자동 생성
 *   PUT    /api/v1/annual-leaves/{id}/reject   반려
 *   PUT    /api/v1/annual-leaves/grant         연차 추가 부여 (관리자)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/annual-leaves")
@RequiredArgsConstructor
public class AnnualLeaveController {

    private final AnnualLeaveService annualLeaveService;

    /**
     * 휴가 신청
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AnnualLeaveResponse>> apply(
            @AuthMemberId Long memberId,
            @Valid @RequestBody AnnualLeaveRequest request
    ) {
        var leave = annualLeaveService.apply(
                memberId,
                request.startDate(),
                request.endDate(),
                request.leaveType(),
                request.reason()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(AnnualLeaveResponse.from(leave)));
    }

    /**
     * 내 휴가 목록 조회
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AnnualLeaveResponse>>> findMyLeaves(
            @AuthMemberId Long memberId
    ) {
        List<AnnualLeaveResponse> responses = annualLeaveService.findMyLeaves(memberId)
                .stream()
                .map(AnnualLeaveResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 연차 잔여일수 조회
     */
    @GetMapping("/me/balance")
    public ResponseEntity<ApiResponse<AnnualLeaveBalanceResponse>> getBalance(
            @AuthMemberId Long memberId,
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year
    ) {
        var balance = annualLeaveService.getBalance(memberId, year);
        return ResponseEntity.ok(ApiResponse.success(AnnualLeaveBalanceResponse.from(balance)));
    }

    /**
     * 대기 중인 휴가 목록 (관리자용)
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<AnnualLeaveResponse>>> findPending() {
        List<AnnualLeaveResponse> responses = annualLeaveService.findPending()
                .stream()
                .map(AnnualLeaveResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 휴가 승인 — 승인 시 기간 전체 WorkRecord(ANNUAL_LEAVE) 자동 생성
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long id) {
        annualLeaveService.approve(id);
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 휴가 반려
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {
        annualLeaveService.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 연차 추가 부여 (관리자용)
     */
    @PutMapping("/grant")
    public ResponseEntity<ApiResponse<AnnualLeaveBalanceResponse>> grant(
            @RequestParam Long memberId,
            @RequestParam int year,
            @RequestParam double days
    ) {
        var balance = annualLeaveService.grant(memberId, year, days);
        return ResponseEntity.ok(ApiResponse.success(AnnualLeaveBalanceResponse.from(balance)));
    }
}
