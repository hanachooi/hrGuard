package dev.commute.service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExternalCommuteRequest {

    @NotNull(message = "memberId는 필수입니다.")
    private Long memberId;

    // 어떤 장치에서 찍혔는지 추적 (예: "GATE-A-01", "FINGERPRINT-B-02")
    @NotBlank(message = "deviceId는 필수입니다.")
    private String deviceId;
}
