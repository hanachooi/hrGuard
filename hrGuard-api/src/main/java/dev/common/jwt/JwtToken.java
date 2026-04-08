package dev.common.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 토큰 데이터 객체 (grantType, accessToken)
@Getter
@Builder
@AllArgsConstructor
public class JwtToken {

    private String grantType;
    private String accessToken;
    private String refreshToken;
}
