package dev.leave.controller.close.user;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.leave.service.LeaveService;
import dev.leave.service.request.AnnualLeaveRequest;
import dev.leave.service.response.AnnualLeaveBalanceResponse;
import dev.leave.service.response.AnnualLeaveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/annual-leaves")
@RequiredArgsConstructor
public class LeaveUserController {

    private final LeaveService leaveService;

    /**
     * 휴가 신청
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AnnualLeaveResponse>> apply(
            @AuthMemberId Long memberId,
            @Valid @RequestBody AnnualLeaveRequest request
    ) {
        var leave = leaveService.apply(
                memberId,
                request.startDateTime(),
                request.endDateTime(),
                request.leaveType(),
                request.reason()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(AnnualLeaveResponse.from(leave)));
    }

    /**
     * 내 휴가 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AnnualLeaveResponse>>> findMyLeaves(
            @AuthMemberId Long memberId
    ) {
        List<AnnualLeaveResponse> responses = leaveService.findMyLeaves(memberId)
                .stream()
                .map(AnnualLeaveResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 내 연차 잔여일수 조회
     */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<AnnualLeaveBalanceResponse>> getBalance(
            @AuthMemberId Long memberId,
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year
    ) {
        var balance = leaveService.getBalance(memberId, year);
        return ResponseEntity.ok(ApiResponse.success(AnnualLeaveBalanceResponse.from(balance)));
    }
}
