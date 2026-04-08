package dev.common.interceptor;

import dev.common.apikey.ApiKeyProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

// /api/external/** 요청 시 API Key가 유효한지 검사. 없거나 틀리면 401 반환
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String API_KEY_PREFIX = "ApiKey ";

    private final ApiKeyProperties apiKeyProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = resolveApiKey(request);

        if (apiKey == null || !apiKey.equals(apiKeyProperties.getKey())) {
            log.warn("유효하지 않은 API Key");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing API Key");
            return false;
        }

        return true;
    }

    private String resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(API_KEY_PREFIX)) {
            return header.substring(API_KEY_PREFIX.length());
        }
        return null;
    }
}
