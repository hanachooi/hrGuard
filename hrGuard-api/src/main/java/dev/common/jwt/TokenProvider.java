package dev.common.jwt;

// 토큰 생성/검증/추출 인터페이스
public interface TokenProvider {

    JwtToken generateToken(Long memberId);

    boolean validateToken(String token);

    Long extractMemberId(String token);
}
