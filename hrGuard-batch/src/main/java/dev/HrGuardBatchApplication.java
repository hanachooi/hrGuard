package dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
public class HrGuardBatchApplication {

    /**
     * 기본 모드: web 상주 + 스케줄러 트리거 (spring.batch.job.enabled=false).
     *
     * <p>cli 단발 실행 모드 (java -jar app.jar --spring.batch.job.enabled=true --spring.batch.job.name=payrollJob ...)
     * 일 때만 Job 종료 후 OS exit code 를 반환한다. 활성화 방법:
     * <ul>
     *   <li>{@code --batch.exit-after-job} 인자</li>
     *   <li>{@code BATCH_EXIT_AFTER_JOB=true} 환경변수</li>
     * </ul>
     *
     * <p>Exit code 는 {@code PayrollJobExecutionListener} (ExitCodeGenerator) 가 결정한다:
     * 0=정상, 1=실패, 2=skip 발생(warning).
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(HrGuardBatchApplication.class, args);

        boolean exitAfterJob = Arrays.asList(args).contains("--batch.exit-after-job")
                || "true".equalsIgnoreCase(System.getenv("BATCH_EXIT_AFTER_JOB"));
        if (exitAfterJob) {
            int code = SpringApplication.exit(ctx);
            System.exit(code);
        }
    }
}
