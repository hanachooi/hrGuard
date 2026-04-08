package dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HrGuardApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrGuardApiApplication.class, args);
    }
}
