package dev.member.service;

import dev.member.entity.Member;
import dev.member.exception.MemberError;
import dev.member.exception.MemberException;
import dev.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberDomainService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member register(String name, String encodedPassword, String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new MemberException(MemberError.EMAIL_DUPLICATED);
        }
        return memberRepository.save(Member.create(name, encodedPassword, email));
    }

    @Transactional(readOnly = true)
    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MemberError.EMAIL_NOT_FOUND));
    }
}
