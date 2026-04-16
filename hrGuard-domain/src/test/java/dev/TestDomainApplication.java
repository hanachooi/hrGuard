package dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * hrGuard-domain 통합 테스트 전용 부트스트랩.
 * <p>
 * 도메인 모듈은 단독 실행 가능한 @SpringBootApplication이 없으므로
 * 테스트 컨텍스트 구동을 위해 test 소스에 최소 진입점을 둔다.
 */
@SpringBootApplication
public class TestDomainApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestDomainApplication.class, args);
    }
}
