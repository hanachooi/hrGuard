package dev.fieldwork.controller.close.user;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.fieldwork.entity.FieldWork;
import dev.fieldwork.service.FieldWorkService;
import dev.fieldwork.service.request.FieldWorkRequest;
import dev.fieldwork.service.response.FieldWorkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/field-works")
@RequiredArgsConstructor
public class FieldWorkUserController {

    private final FieldWorkService fieldWorkService;

    /**
     * 외근 신청
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FieldWorkResponse>> apply(
            @AuthMemberId Long memberId,
            @Valid @RequestBody FieldWorkRequest request
    ) {
        FieldWork fieldWork = fieldWorkService.apply(
                memberId,
                request.startDateTime(),
                request.endDateTime(),
                request.location(),
                request.purpose()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(FieldWorkResponse.from(fieldWork)));
    }

    /**
     * 내 외근 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FieldWorkResponse>>> findMyFieldWorks(
            @AuthMemberId Long memberId
    ) {
        List<FieldWorkResponse> responses = fieldWorkService.findMyFieldWorks(memberId)
                .stream()
                .map(FieldWorkResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
