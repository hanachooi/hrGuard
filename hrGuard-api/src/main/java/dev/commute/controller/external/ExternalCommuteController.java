package dev.commute.controller.external;

import dev.common.dto.ApiResponse;
import dev.commute.service.CommuteService;
import dev.commute.service.CommuteTypeResolver;
import dev.commute.service.request.ExternalCommuteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/external/commute")
@RequiredArgsConstructor
public class ExternalCommuteController {

    private final CommuteService commuteService;
    private final CommuteTypeResolver commuteTypeResolver;

    // 외부 장치 인증: Authorization: ApiKey {key}
    // memberId는 토큰 대신 요청 Body에서 직접 받음
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @RequestBody @Valid ExternalCommuteRequest request
    ) {
        commuteService.checkIn(request.getMemberId());
        commuteTypeResolver.resolve(request.getMemberId(), LocalDate.now());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.successEmpty());
    }

    @PatchMapping("/check-out")
    public ResponseEntity<ApiResponse<Void>> checkOut(
            @RequestBody @Valid ExternalCommuteRequest request
    ) {
        commuteService.checkOut(request.getMemberId());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.successEmpty());
    }
}
