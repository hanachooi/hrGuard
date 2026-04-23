package dev.test;

import jakarta.persistence.*;
import lombok.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Persistable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@SpringBootTest
class saveMethodTest {

    private final int DATA_COUNT = 10000; // 1만 건 (차이를 확인하기 적당한 수치)
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostWithSequenceIdRepository postWithSequenceIdRepository;
    @Autowired
    private PostWithCustomIdRepository postWithCustomIdRepository;

    @AfterEach
    void tearDown() {
        postRepository.deleteAllInBatch();
        postWithSequenceIdRepository.deleteAllInBatch();
        postWithCustomIdRepository.deleteAllInBatch();
    }


    private List<Post> setMockData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Post.builder()
                        .postTitle("테스트 제목 " + i)
                        .postContent("테스트 내용 " + i)
                        .build())
                .toList();
    }

    private List<PostWithSequenceId> setMockSequenceData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> PostWithSequenceId.builder()
                        .postTitle("테스트 제목 " + i)
                        .postContent("테스트 내용 " + i)
                        .build())
                .toList();
    }

    private List<PostWithCustomId> setMockCustomIdData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> PostWithCustomId.create("테스트 제목 " + i, "테스트 내용 " + i))
                .toList();
    }

    @Test
    @DisplayName("1. JPA save() 단건 반복: 가장 느림 (N번의 Insert)")
    void saveLoopTest() {
        List<Post> mockList = setMockData(DATA_COUNT);
        StopWatch sw = new StopWatch();

        sw.start();
        for (Post post : mockList) {
            postRepository.save(post);
        }
        sw.stop();

        System.out.println(">>> JPA save() 반복 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @DisplayName("2. JPA saveAll(): IDENTITY 전략에선 1번과 큰 차이 없음")
    void saveAllTest() {
        List<Post> mockList = setMockData(DATA_COUNT);
        StopWatch sw = new StopWatch();

        sw.start();
        postRepository.saveAll(mockList);
        sw.stop();

        System.out.println(">>> JPA saveAll() 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @Transactional
    @DisplayName("3. JPA save() 반복 + @Transactional: saveAll()과 동일한 트랜젝션 적용")
    void saveLoopWithTransactionTest() {
        List<Post> mockList = setMockData(DATA_COUNT);
        StopWatch sw = new StopWatch();

        sw.start();
        for (Post post : mockList) {
            postRepository.save(post);
        }
        sw.stop();

        System.out.println(">>> JPA save() + @Transactional 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @DisplayName("4. [SEQUENCE 전략] saveAll(): ID 선점 → Hibernate 배치 → rewriteBatchedStatements로 멀티값 INSERT")
    void saveAllWithSequenceIdTest() {
        // IDENTITY와 달리 Hibernate가 sequence 테이블에서 ID를 미리 할당(allocationSize=50)
        // → INSERT 시점에 ID가 이미 확정 → hibernate.jdbc.batch_size 단위로 JDBC 배치 전송
        // → MySQL 드라이버가 rewriteBatchedStatements=true 로 멀티값 INSERT로 재작성
        // profileSQL 로그에서 "INSERT INTO post_table_seq ... VALUES (...),(...),..." 형태 확인
        List<PostWithSequenceId> mockList = setMockSequenceData(DATA_COUNT);
        StopWatch sw = new StopWatch();

        sw.start();
        postWithSequenceIdRepository.saveAll(mockList);
        sw.stop();

        System.out.println(">>> [SEQUENCE 전략] saveAll() 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @DisplayName("5. [UUID 자체 ID 전략 + Persistable] saveAll(): SELECT 생략 → Batch INSERT 확인")
    void saveAllWithCustomIdTest() {
        // UUID를 생성자에서 직접 할당 + isNew()=true → Spring Data가 SELECT 없이 em.persist() 직행
        // → hibernate.jdbc.batch_size 단위 JDBC 배치 → 멀티값 INSERT로 재작성
        // profileSQL 로그에서 "INSERT INTO post_table_custom_id ... VALUES (...),(...),..." 형태 확인
        List<PostWithCustomId> mockList = setMockCustomIdData(DATA_COUNT);
        StopWatch sw = new StopWatch();

        sw.start();
        postWithCustomIdRepository.saveAll(mockList);
        sw.stop();

        System.out.println(">>> [UUID 자체 ID 전략] saveAll() 소요시간: " + sw.getTotalTimeMillis() + "ms");
    }

    // ── 테스트용 엔티티 ────────────────────────────────────────────────────────

    @Entity
    @Table(name = "post_table")
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Post {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String postTitle;
        private String postContent;
    }

    // IDENTITY 포기 후 대안 1: Sequence(테이블 에뮬레이션) 전략
    // allocationSize=50 → 50개 단위로 ID 선점, INSERT 직전 DB 왕복 없음
    @Entity
    @Table(name = "post_table_seq")
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PostWithSequenceId {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "post_seq_gen")
        @SequenceGenerator(name = "post_seq_gen", sequenceName = "post_sequence", allocationSize = 50)
        private Long id;
        private String postTitle;
        private String postContent;
    }

    // IDENTITY 포기 후 대안 2: UUID 자체 할당 + Persistable
    // isNew()=true → Spring Data가 existsById SELECT를 생략하고 바로 persist
    @Entity
    @Table(name = "post_table_custom_id")
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PostWithCustomId implements Persistable<String> {
        @Transient
        private final boolean isNew = true;
        @Id
        private String id;
        private String postTitle;
        private String postContent;

        private PostWithCustomId(String postTitle, String postContent) {
            this.id = UUID.randomUUID().toString();
            this.postTitle = postTitle;
            this.postContent = postContent;
        }

        public static PostWithCustomId create(String postTitle, String postContent) {
            return new PostWithCustomId(postTitle, postContent);
        }

        @Override
        public boolean isNew() {
            return isNew;
        }
    }

}