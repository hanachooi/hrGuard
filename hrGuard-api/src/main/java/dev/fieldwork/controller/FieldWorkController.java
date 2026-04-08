package dev.attendance.fieldwork.controller;

import dev.attendance.fieldwork.service.FieldWorkService;
import dev.attendance.fieldwork.service.request.FieldWorkRequest;
import dev.attendance.fieldwork.service.request.RejectRequest;
import dev.attendance.fieldwork.service.response.FieldWorkResponse;
import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 외근 신청/승인 API.
 *
 * <h3>엔드포인트</h3>
 * <pre>
 *   POST   /api/v1/field-works             본인 외근 신청
 *   GET    /api/v1/field-works/me          내 외근 목록 조회
 *   GET    /api/v1/field-works/pending     대기 중인 외근 목록 (관리자)
 *   PUT    /api/v1/field-works/{id}/approve 승인 → WorkRecord(FIELD) 자동 생성
 *   PUT    /api/v1/field-works/{id}/reject  반려
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/field-works")
@RequiredArgsConstructor
public class FieldWorkController {

    private final FieldWorkService fieldWorkService;

    /**
     * 외근 신청
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FieldWorkResponse>> apply(
            @AuthMemberId Long memberId,
            @Valid @RequestBody FieldWorkRequest request
    ) {
        var fieldWork = fieldWorkService.apply(
                memberId,
                request.workDate(),
                request.startTime(),
                request.endTime(),
                request.location(),
                request.purpose()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(FieldWorkResponse.from(fieldWork)));
    }

    /**
     * 내 외근 목록 조회
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<FieldWorkResponse>>> findMyFieldWorks(
            @AuthMemberId Long memberId
    ) {
        List<FieldWorkResponse> responses = fieldWorkService.findMyFieldWorks(memberId)
                .stream()
                .map(FieldWorkResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 대기 중인 외근 목록 (관리자용)
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<FieldWorkResponse>>> findPending() {
        List<FieldWorkResponse> responses = fieldWorkService.findPending()
                .stream()
                .map(FieldWorkResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 외근 승인 — 승인 시 WorkRecord(FIELD) 자동 생성
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long id) {
        fieldWorkService.approve(id);
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 외근 반려
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {
        fieldWorkService.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }
}
