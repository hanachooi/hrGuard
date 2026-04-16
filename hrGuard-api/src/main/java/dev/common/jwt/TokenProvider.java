package dev.common.jwt;

import dev.member.constant.MemberRole;

// 토큰 생성/검증/추출 인터페이스
public interface TokenProvider {

    JwtToken generateToken(Long memberId, MemberRole role);

    boolean validateToken(String token);

    Long extractMemberId(String token);

    MemberRole extractRole(String token);
}
