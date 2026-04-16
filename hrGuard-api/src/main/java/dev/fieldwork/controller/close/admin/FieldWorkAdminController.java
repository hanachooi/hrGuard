package dev.fieldwork.controller.close.admin;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.fieldwork.service.FieldWorkService;
import dev.fieldwork.service.request.RejectRequest;
import dev.fieldwork.service.response.FieldWorkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/field-works")
@RequiredArgsConstructor
public class FieldWorkAdminController {

    private final FieldWorkService fieldWorkService;

    /**
     * 대기 중인 외근 목록
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<FieldWorkResponse>>> findPending(
            @AuthMemberId Long memberId
    ) {
        List<FieldWorkResponse> responses = fieldWorkService.findPending()
                .stream()
                .map(FieldWorkResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 외근 승인 — 승인 시 WorkRecord(FIELD) 슬롯 자동 점유
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @AuthMemberId Long memberId,
            @PathVariable Long id
    ) {
        fieldWorkService.approve(id);
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }

    /**
     * 외근 반려
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthMemberId Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {
        fieldWorkService.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successEmpty());
    }
}
