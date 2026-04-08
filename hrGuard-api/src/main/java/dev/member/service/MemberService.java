package dev.member.service;

import dev.common.jwt.JwtToken;
import dev.common.jwt.TokenProvider;
import dev.member.entity.Member;
import dev.member.exception.MemberError;
import dev.member.exception.MemberException;
import dev.member.service.request.SignInRequest;
import dev.member.service.request.SignUpRequest;
import dev.member.service.response.JwtTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberDomainService memberDomainService;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public void signUp(SignUpRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new MemberException(MemberError.PASSWORD_NOT_MATCHED);
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        memberDomainService.register(request.getName(), encodedPassword, request.getEmail());
    }

    @Transactional(readOnly = true)
    public JwtTokenResponse signIn(SignInRequest request) {
        Member member = memberDomainService.findByEmail(request.getEmail());
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new MemberException(MemberError.PASSWORD_NOT_MATCHED);
        }
        JwtToken token = tokenProvider.generateToken(member.getId());
        return JwtTokenResponse.from(token);
    }
}
