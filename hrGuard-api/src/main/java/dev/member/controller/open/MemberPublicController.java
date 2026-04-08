package dev.member.controller.open;

import dev.common.dto.ApiResponse;
import dev.member.service.MemberService;
import dev.member.service.request.SignInRequest;
import dev.member.service.request.SignUpRequest;
import dev.member.service.response.JwtTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/members")
public class MemberPublicController {

    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Validated @RequestBody SignUpRequest request
    ) {
        memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.successEmpty());
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<JwtTokenResponse>> signin(
            @Validated @RequestBody SignInRequest request
    ) {
        JwtTokenResponse response = memberService.signIn(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
