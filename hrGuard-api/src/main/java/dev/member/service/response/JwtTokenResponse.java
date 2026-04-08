package dev.member.service.response;

import dev.common.jwt.JwtToken;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JwtTokenResponse {
    private String accessToken;
    private String refreshToken;

    public static JwtTokenResponse from(JwtToken token) {
        return new JwtTokenResponse(token.getAccessToken(), token.getRefreshToken());
    }
}
