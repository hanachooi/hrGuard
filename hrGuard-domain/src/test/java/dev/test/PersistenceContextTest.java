package dev.test;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PersistenceContextTest {

    @Autowired
    private PcItemRepository repository; // dev.test.PcItemRepository (별도 파일)

    @PersistenceContext(unitName = "domainUnit")
    private EntityManager em;

    @Autowired
    @Qualifier("domainTransactionManager")
    private PlatformTransactionManager txManager;

    @AfterEach
    void tearDown() {
        repository.deleteAllInBatch();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 1. 영속성 컨텍스트 생명주기
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("1. 1차 캐시: 같은 트랜잭션에서 동일 ID 두 번 find → SELECT 1회만 발생, 이후는 캐시 히트")
    void firstLevelCache() {
        Long id = repository.save(PcItem.builder().name("item").value(1L).build()).getId();
        em.clear(); // save로 PC에 올라간 엔티티를 비워 다음 find가 SELECT를 발생시키도록

        StopWatch sw = new StopWatch();
        sw.start();
        PcItem first = em.find(PcItem.class, id); // SELECT 발생 → PC에 등록
        PcItem second = em.find(PcItem.class, id); // 캐시 히트   → SELECT 미발생
        sw.stop();

        assertThat(first).isSameAs(second); // 동일 인스턴스 = 캐시 히트 증명
        System.out.println(">>> 1차 캐시 히트 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @DisplayName("2. PC는 트랜잭션 범위: 트랜잭션이 끝나면 PC가 사라지고 다음 트랜잭션은 새 PC로 시작")
    void persistenceContextLifecycle() {
        // 두 개의 별개 트랜잭션으로 "PC가 tx마다 새로 생성됨"을 보여야 하므로 TransactionTemplate 필요
        Long id = repository.save(PcItem.builder().name("item").value(1L).build()).getId();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        StopWatch sw = new StopWatch();

        sw.start("tx1");
        PcItem fromTx1 = tx.execute(status -> em.find(PcItem.class, id)); // SELECT — tx1 PC
        sw.stop();

        sw.start("tx2");
        PcItem fromTx2 = tx.execute(status -> em.find(PcItem.class, id)); // SELECT — tx2 PC (새로 생성)
        sw.stop();

        assertThat(fromTx1).isNotSameAs(fromTx2);               // PC가 달라서 인스턴스도 다름
        assertThat(fromTx1.getId()).isEqualTo(fromTx2.getId());  // 데이터는 동일

        System.out.println(">>> tx1 소요시간: " + sw.getTaskInfo()[0].getTimeMillis() + "ms");
        System.out.println(">>> tx2 소요시간: " + sw.getTaskInfo()[1].getTimeMillis() + "ms");
        // profileSQL 로그: 동일한 SELECT가 2번 출력됨
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. Dirty Checking
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("3. Dirty Checking: 트랜잭션 내 엔티티 변경 → save() 없이도 flush 시 UPDATE 자동 발생")
    void dirtyChecking() {
        Long id = repository.save(PcItem.builder().name("before").value(1L).build()).getId();
        em.clear(); // PC 비워 아래 find가 스냅샷을 새로 찍도록

        StopWatch sw = new StopWatch();
        sw.start();
        PcItem item = em.find(PcItem.class, id); // 스냅샷 저장
        item.changeName("after");                // 변경 감지 대상 — save() 없음
        em.flush();                              // 스냅샷과 비교 → UPDATE SQL 실행
        sw.stop();

        em.clear();
        assertThat(em.find(PcItem.class, id).getName()).isEqualTo("after");
        System.out.println(">>> Dirty Checking UPDATE 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @DisplayName("4. 트랜잭션 없으면 Dirty Checking 비활성: 엔티티를 변경해도 DB에 반영되지 않음")
    void noDirtyCheckingWithoutTransaction() {
        Long id = repository.save(PcItem.builder().name("before").value(1L).build()).getId();
        StopWatch sw = new StopWatch();

        sw.start();
        // 트랜잭션 없음 → findById 직후 PC가 닫힘 → 준영속(detached) 상태 반환
        PcItem detached = repository.findById(id).orElseThrow();
        detached.changeName("after"); // 추적하는 PC가 없음 → flush 없음 → DB 반영 X
        sw.stop();

        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("before");
        System.out.println(">>> 트랜잭션 없는 변경 시도 소요시간: " + sw.getTotalTimeMillis() + "ms");
        // profileSQL 로그: UPDATE가 출력되지 않음
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. Flush 시점 & 쓰기 지연(Write-behind)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("5. 쓰기 지연(Write-behind): persist() 직후 INSERT 미발생 → flush 시점에 일괄 실행")
    void writeBehind() {
        StopWatch sw = new StopWatch();
        sw.start();

        for (int i = 0; i < 5; i++) {
            em.persist(PcItem.builder().name("item-" + i).value((long) i).build());
            // persist() 시점에 INSERT가 즉시 나가지 않고 쓰기 지연 저장소에 쌓임
            // (단, IDENTITY 전략은 ID 채번을 위해 persist() 즉시 INSERT가 발생할 수 있음)
        }

        sw.stop();

        Long count = em.createQuery("SELECT COUNT(p) FROM PcItem p", Long.class).getSingleResult();
        assertThat(count).isEqualTo(5);
        System.out.println(">>> 쓰기 지연 INSERT 5건 소요시간: " + sw.getTotalTimeMillis() + "ms");
        // profileSQL 로그: persist() 직후가 아닌 flush 시점에 INSERT가 출력됨
    }

    @Test
    @Transactional
    @DisplayName("6. JPQL 실행 직전 flush: persist() 후 JPQL 조회 → INSERT가 먼저 실행된 뒤 SELECT 발생")
    void flushBeforeJpql() {
        PcItem newItem = PcItem.builder().name("new").value(99L).build();
        em.persist(newItem); // INSERT 아직 미실행, 쓰기 지연 저장소에 쌓임

        StopWatch sw = new StopWatch();
        sw.start();
        // JPQL 실행 직전 FlushMode.AUTO → flush 강제 발동
        // profileSQL 순서: 1) INSERT pc_item ... 2) SELECT ... FROM pc_item
        List<PcItem> result = em.createQuery("SELECT p FROM PcItem p", PcItem.class).getResultList();
        sw.stop();

        assertThat(result).anyMatch(p -> "new".equals(p.getName())); // flush 덕분에 조회됨
        System.out.println(">>> JPQL 직전 flush 소요시간: " + sw.getTotalTimeMillis() + "ms");
        // profileSQL 로그: INSERT 먼저 → SELECT 나중 (순서 중요)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. readOnly 트랜잭션
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("7. readOnly=true: flush 억제 → 엔티티를 변경해도 DB에 반영되지 않음")
    void readOnlySuppressesFlush() {
        // 데이터를 먼저 커밋한 뒤 readOnly tx에서 검증해야 하므로 TransactionTemplate 필요
        Long id = repository.save(PcItem.builder().name("before").value(1L).build()).getId();

        TransactionTemplate readOnlyTx = new TransactionTemplate(txManager);
        readOnlyTx.setReadOnly(true);
        // FlushMode.MANUAL → 트랜잭션 커밋 시 flush 자체가 발동되지 않음

        StopWatch sw = new StopWatch();
        sw.start();
        readOnlyTx.execute(status -> {
            PcItem item = em.find(PcItem.class, id);
            item.changeName("after"); // 변경되지만 flush가 억제됨 → DB 반영 X
            return null;
        });
        sw.stop();

        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("before");
        System.out.println(">>> readOnly 트랜잭션 소요시간: " + sw.getTotalTimeMillis() + "ms");
        // profileSQL 로그: UPDATE가 출력되지 않음
    }

    // ── 테스트용 엔티티 ────────────────────────────────────────────────────────

    @Entity(name = "PcItem")
    @Table(name = "pc_item")
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PcItem {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        private Long value;

        private PcItem(Long id, String name, Long value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public void changeName(String name) {
            this.name = name;
        }
    }
}
