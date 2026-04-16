package dev.businesstrip.controller.close.admin;

import dev.businesstrip.service.BusinessTripService;
import dev.businesstrip.service.request.RejectRequest;
import dev.businesstrip.service.response.BusinessTripResponse;
import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/business-trips")
@RequiredArgsConstructor
public class BusinessTripAdminController {

    private final BusinessTripService businessTripService;

    /**
     * 출장 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BusinessTripResponse>>> findPending(
            @AuthMemberId Long memberId
    ) {
        List<BusinessTripResponse> responses = businessTripService.findPending()
                .stream()
                .map(BusinessTripResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 출장 승인 — 승인 시 출장 기간 전체 WorkRecord(BUSINESS_TRIP) 자동 생성
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @AuthMemberId Long memberId,
            @PathVariable Long id
    ) {
        businessTripService.approve(id);
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 출장 반려
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthMemberId Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {
        businessTripService.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }
}
