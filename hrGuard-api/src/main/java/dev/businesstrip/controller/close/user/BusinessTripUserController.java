package dev.businesstrip.controller.close.user;

import dev.businesstrip.entity.BusinessTrip;
import dev.businesstrip.service.BusinessTripService;
import dev.businesstrip.service.request.BusinessTripRequest;
import dev.businesstrip.service.response.BusinessTripResponse;
import dev.common.dto.ApiResponse;
import dev.common.identity.annotation.AuthMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business-trips")
@RequiredArgsConstructor
public class BusinessTripUserController {

    private final BusinessTripService businessTripService;

    /**
     * 출장 신청
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BusinessTripResponse>> apply(
            @AuthMemberId Long memberId,
            @Valid @RequestBody BusinessTripRequest request
    ) {
        BusinessTrip trip = businessTripService.apply(
                memberId,
                request.startDateTime(),
                request.endDateTime(),
                request.destination(),
                request.purpose()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BusinessTripResponse.from(trip)));
    }

    /**
     * 내 출장 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BusinessTripResponse>>> findMyTrips(
            @AuthMemberId Long memberId
    ) {
        List<BusinessTripResponse> responses = businessTripService.findMyTrips(memberId)
                .stream()
                .map(BusinessTripResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
