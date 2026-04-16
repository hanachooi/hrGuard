package dev.member.entity;

import dev.common.BaseEntity;
import dev.member.constant.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Builder
    public Member(
            String name,
            String password,
            String email,
            MemberRole role
    ) {
        this.name = name;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    public static Member create(
            String name,
            String encodedPassword,
            String email
    ) {
        return Member.builder()
                .name(name)
                .password(encodedPassword)
                .email(email)
                .role(MemberRole.USER)
                .build();
    }
}
