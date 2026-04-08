package dev.commute.controller.close;

import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import dev.commute.service.CommuteService;
import dev.commute.service.CommuteTypeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/commute")
@RequiredArgsConstructor
public class CommuteCloseController {

    private final CommuteService commuteService;
    private final CommuteTypeResolver commuteTypeResolver;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @AuthMemberId Long memberId
    ) {
        // 트랜잭션 커밋 완료 후 응답 → 이후 비동기로 commuteType 결정
        commuteService.checkIn(memberId);
        commuteTypeResolver.resolve(memberId, LocalDate.now());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.successEmpty());
    }

    @PatchMapping("/check-out")
    public ResponseEntity<ApiResponse<Void>> checkOut(
            @AuthMemberId Long memberId
    ) {
        commuteService.checkOut(memberId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.successEmpty());
    }
}
