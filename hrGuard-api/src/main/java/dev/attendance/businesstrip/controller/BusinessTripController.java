package dev.attendance.businesstrip.controller;

import dev.attendance.businesstrip.service.BusinessTripService;
import dev.attendance.businesstrip.service.request.BusinessTripRequest;
import dev.attendance.businesstrip.service.request.RejectRequest;
import dev.attendance.businesstrip.service.response.BusinessTripResponse;
import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 출장 신청/승인 API.
 *
 * <h3>엔드포인트</h3>
 * <pre>
 *   POST   /api/v1/business-trips          본인 출장 신청
 *   GET    /api/v1/business-trips/me       내 출장 목록 조회
 *   GET    /api/v1/business-trips/pending  대기 중인 출장 목록 (관리자)
 *   PUT    /api/v1/business-trips/{id}/approve  승인 → WorkRecord 자동 생성
 *   PUT    /api/v1/business-trips/{id}/reject   반려
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/business-trips")
@RequiredArgsConstructor
public class BusinessTripController {

    private final BusinessTripService businessTripService;

    /**
     * 출장 신청
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BusinessTripResponse>> apply(
            @AuthMemberId Long memberId,
            @Valid @RequestBody BusinessTripRequest request
    ) {
        var trip = businessTripService.apply(
                memberId,
                request.startDate(),
                request.endDate(),
                request.destination(),
                request.purpose()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BusinessTripResponse.from(trip)));
    }

    /**
     * 내 출장 목록 조회
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<BusinessTripResponse>>> findMyTrips(
            @AuthMemberId Long memberId
    ) {
        List<BusinessTripResponse> responses = businessTripService.findMyTrips(memberId)
                .stream()
                .map(BusinessTripResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 대기 중인 출장 목록 (관리자용)
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<BusinessTripResponse>>> findPending() {
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
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long id) {
        businessTripService.approve(id);
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 출장 반려
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {
        businessTripService.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }
}
