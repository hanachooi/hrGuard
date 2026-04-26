package dev.test;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.stream.IntStream;

@SpringBootTest
class DirtyCheckCostTest {

    static final int DATA_COUNT = 3000;

    @Autowired
    private DcItemRepository repository; // dev.test.DcItemRepository (별도 파일)

    @Autowired
    @Qualifier("domainTransactionManager")
    private PlatformTransactionManager txManager;

    @Autowired
    @Qualifier("domainEntityManagerFactory")
    private EntityManagerFactory emf;

    private List<Long> ids;

    private Statistics stats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private void printStats(String tag) {
        Statistics s = stats();
        System.out.printf(">>> [%s] flush=%d, prepared=%d, entityLoad=%d, query=%d, dirtyOptimisticFailure=%d%n",
                tag, s.getFlushCount(), s.getPrepareStatementCount(),
                s.getEntityLoadCount(), s.getQueryExecutionCount(),
                s.getOptimisticFailureCount());
    }

    @BeforeEach
    void setUp() {
        // REQUIRES_NEW: readOnly=true 테스트 트랜잭션이 이미 열려있어도 독립된 쓰기 트랜잭션을 강제로 열고 커밋
        // 커밋 후 PC가 닫히므로 각 테스트의 트랜잭션은 빈 PC에서 시작함
        TransactionTemplate writeTx = new TransactionTemplate(txManager);
        writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ids = writeTx.execute(status ->
                IntStream.range(0, DATA_COUNT)
                        .mapToObj(i -> repository.save(DcItem.builder().name("item-" + i).value((long) i).build()).getId())
                        .toList()
        );
        // setUp의 INSERT 통계는 측정에서 제외 — 테스트 본문 직전에 stats 초기화
        Statistics s = stats();
        s.setStatisticsEnabled(true);
        s.clear();
    }

    @AfterEach
    void tearDown() {
        // REQUIRES_NEW: 테스트 트랜잭션(readOnly 포함)과 무관하게 독립된 쓰기 트랜잭션으로 정리
        TransactionTemplate writeTx = new TransactionTemplate(txManager);
        writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writeTx.executeWithoutResult(status -> repository.deleteAllInBatch());
    }

    @Test
    @Transactional
    @DisplayName("1. 단건 루프: 3,000건 findByValue(JPQL)를 하나씩 반복 — 매 query 직전 PC 전체 dirty check, 뒤로 갈수록 느려짐")
    void loopFind() {
        StopWatch sw = new StopWatch();

        sw.start();
        for (long v = 0; v < DATA_COUNT; v++) {
            repository.findByValue(v);
            // JPQL 실행 직전 onAutoFlush → PC에 쌓인 엔티티 전부 dirty check
            // PC: 1개 → 2개 → ... → 3000개 → dirty check 비용이 O(N)씩 증가
        }
        sw.stop();

        System.out.println(">>> 단건 루프 소요시간: " + sw.getTotalTimeMillis() + "ms");
        printStats("단건 루프(JPQL)");
    }

    @Test
    @Transactional
    @DisplayName("2. IN 쿼리: 3,000건 한 번에 조회 — dirty check 1회, 네트워크 왕복 1회")
    void inQuery() {
        StopWatch sw = new StopWatch();

        sw.start();
        repository.findAllById(ids); // WHERE id IN (?, ?, ...) 1번
        sw.stop();

        System.out.println(">>> IN 쿼리 소요시간: " + sw.getTotalTimeMillis() + "ms");
        printStats("IN 쿼리");
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("3. 단건 루프 + readOnly=true: dirty check가 0번 — 네트워크 왕복은 그대로 N번이지만 CPU 비용 제거")
    void loopFindWithReadOnly() {
        // readOnly=true → FlushMode.MANUAL → onAutoFlush 훅 자체가 호출되지 않음
        StopWatch sw = new StopWatch();

        sw.start();
        for (long v = 0; v < DATA_COUNT; v++) {
            repository.findByValue(v);
        }
        sw.stop();

        System.out.println(">>> 단건 루프 + readOnly 소요시간: " + sw.getTotalTimeMillis() + "ms");
        printStats("단건 루프(readOnly)");
        // 1번보다 빠름 → dirty check가 병목이었다는 증거
        // 2번보다 느림 → 네트워크 왕복(N번)은 여전히 남아있다는 증거
    }

    // ── 테스트용 엔티티 ────────────────────────────────────────────────────────

    @Entity(name = "DcItem")
    @Table(name = "dc_item")
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class DcItem {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        private Long value;

        private DcItem(Long id, String name, Long value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }
    }
}
