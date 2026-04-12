package dev.common.interceptor;

import dev.member.constant.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

// /api/v1/admin/** 요청 시 ADMIN 역할 여부를 검사. ADMIN이 아니면 403 반환
@Slf4j
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object roleAttr = request.getAttribute("memberRole");

        if (roleAttr != MemberRole.ADMIN) {
            log.warn("관리자 권한 없음: role={}, uri={}", roleAttr, request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required");
            return false;
        }

        return true;
    }
}
