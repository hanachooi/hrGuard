package dev.common.configuration;

import dev.common.apikey.ApiKeyProperties;
import dev.common.identity.resolver.AuthMemberIdArgumentResolver;
import dev.common.interceptor.ApiKeyAuthInterceptor;
import dev.common.interceptor.JwtAuthInterceptor;
import dev.common.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

// 인터셉터 등록, SPA 포워딩, 인증 경로 설정
@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    public static final String CLOSED_API_PREFIX = "/api/v1/**";
    public static final String PUBLIC_API_PREFIX = "/api/public/**";
    public static final String EXTERNAL_API_PREFIX = "/api/external/**";
    public static final String VIEW_PAGE_PREFIX = "/view/**";

    private final AuthMemberIdArgumentResolver authResolver;
    private final TokenProvider tokenProvider;
    private final ApiKeyProperties apiKeyProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 내부 앱용: JWT 토큰 검증
        registry.addInterceptor(new JwtAuthInterceptor(tokenProvider))
                .addPathPatterns(CLOSED_API_PREFIX)
                .excludePathPatterns(
                        PUBLIC_API_PREFIX,
                        VIEW_PAGE_PREFIX,
                        "/favicon.ico",
                        "/static/**",
                        "/css/**",
                        "/images/**",
                        "/assets/**",
                        "/index.html"
                );

        // 외부 장치/시스템용: API Key 검증
        registry.addInterceptor(new ApiKeyAuthInterceptor(apiKeyProperties))
                .addPathPatterns(EXTERNAL_API_PREFIX);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
