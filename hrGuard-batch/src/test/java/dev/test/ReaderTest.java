package dev.test;

import dev.batch.payroll.dto.PayrollInputDto;
import dev.common.configuration.DataSourceConfig;
import dev.common.configuration.TransactionManagerConfig;
import dev.payrollpolicy.repository.PayrollPolicyRepository;
import dev.payrollpolicy.repository.projection.PayrollPolicyProjection;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workrecord.repository.projection.WorkRecordProjection;
import dev.workschedule.repository.WorkScheduleRepository;
import dev.workschedule.repository.projection.WorkScheduleProjection;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reader 방식별 메모리 사용 패턴 PoC 테스트.
 * <p>
 * - JdbcCursorItemReader  : DB 커서 스트리밍 → 메모리 사용량 bounded
 * - JdbcPagingItemReader  : pageSize 단위 적재 → 메모리 사용량 bounded
 * - ListItemReader        : PayrollStepConfig.payrollReader 와 동일한 1+3N 전체 선적재 → OOM 위험
 * <p>
 * 로컬 DB 에 work_record 데이터가 존재해야 한다 (-Dpayroll.yearMonth=YYYY-MM, 기본 2026-03).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderTest {

    private static final Logger log = LoggerFactory.getLogger(ReaderTest.class);

    private static final int PAGE_SIZE = 100;   // Reader 의 DB fetch 단위
    private static final int CHUNK_SIZE = 100;   // Step 의 read→process→write 단위 (write 후 참조 해제)
    private static final String YEAR_MONTH = System.getProperty("payroll.yearMonth", "2026-03");

    @Autowired
    @Qualifier(DataSourceConfig.DOMAIN_DATASOURCE)
    private DataSource dataSource;

    @Autowired
    @Qualifier(TransactionManagerConfig.DOMAIN_ENTITY_MANAGER_FACTORY)
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private WorkRecordRepository workRecordRepository;
    @Autowired
    private WorkScheduleRepository workScheduleRepository;
    @Autowired
    private PayrollPolicyRepository payrollPolicyRepository;

    private LocalDate from;
    private LocalDate to;

    // ──────────────────────────────────────────────────────────────────
    // 생명주기
    // ──────────────────────────────────────────────────────────────────

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * 운영 배치는 Reader 가 곧 memberId 스트림 (SRP).
     * 테스트도 동일하게 — @BeforeAll 에서는 날짜 범위만 결정하고 어떤 데이터도 적재하지 않는다.
     * 각 테스트가 자체 Reader 로 처리하면서 흘려보낸 건수를 검증한다.
     */
    @BeforeAll
    void setUpAll() {
        YearMonth ym = YearMonth.parse(YEAR_MONTH);
        this.from = ym.atDay(1);
        this.to = ym.atEndOfMonth();
        log.info("[BeforeAll] yearMonth={}, range=[{} ~ {}]", YEAR_MONTH, from, to);
    }

    @BeforeEach
    void logMemoryBefore(TestInfo info) {
        System.gc();
        log.info("[BeforeEach] ▶ {} | heap={}MB", info.getDisplayName(), mb(usedHeap()));
    }

    @AfterEach
    void logMemoryAfter(TestInfo info) {
        log.info("[AfterEach]  ◀ {} | heap={}MB", info.getDisplayName(), mb(usedHeap()));
    }

    // ──────────────────────────────────────────────────────────────────
    // cursor 방식
    // ──────────────────────────────────────────────────────────────────

    @AfterAll
    void tearDownAll() {
        log.info("[AfterAll] 테스트 종료. 데이터는 로컬 DB 에 유지됩니다.");
    }

    @Test
    @DisplayName("[cursor] chunk 단위로 누적/flush — heap 은 CHUNK_SIZE 만큼만 점유 (톱니 패턴)")
    void jdbcCursorReader_processesAllWithoutOom() throws Exception {
        JdbcCursorItemReader<Long> reader = buildCursorReader();
        reader.open(new ExecutionContext());

        int count = drainWithChunk(reader, "cursor");
        reader.close();

        assertThat(count)
                .as("최소 한 chunk(=%d건) 이상 흘려보내야 톱니 패턴이 의미를 가짐", CHUNK_SIZE)
                .isGreaterThanOrEqualTo(CHUNK_SIZE);
        log.info("[cursor] 총 처리 건수={}", count);
    }

    // ──────────────────────────────────────────────────────────────────
    // paging 방식
    // ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[cursor 원리 확인] read() 호출마다 단일 항목을 반환하고 서로 다른 값이다 (이전 항목은 GC 대상)")
    void jdbcCursorReader_returnsOneItemPerRead() throws Exception {
        JdbcCursorItemReader<Long> reader = buildCursorReader();
        reader.open(new ExecutionContext());

        Long first = reader.read();
        Long second = reader.read();
        reader.close();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        // 연속된 두 항목은 서로 다른 memberId — read() 마다 ResultSet 을 한 칸 전진
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("[paging] chunk 단위로 누적/flush — heap 은 CHUNK_SIZE 만큼만 점유 (톱니 패턴)")
    void jdbcPagingReader_processesAllWithoutOom() throws Exception {
        JdbcPagingItemReader<Long> reader = buildPagingReader(dataSource);
        reader.open(new ExecutionContext());

        int count = drainWithChunk(reader, "paging");
        reader.close();

        assertThat(count)
                .as("최소 한 chunk(=%d건) 이상 흘려보내야 톱니 패턴이 의미를 가짐", CHUNK_SIZE)
                .isGreaterThanOrEqualTo(CHUNK_SIZE);
        log.info("[paging] 총 처리 건수={}", count);
    }

    @Test
    @DisplayName("[paging 원리 확인] 한 페이지 SELECT 로 pageSize 건 서빙 — 페이지 경계에서만 다음 SELECT 발생")
    void jdbcPagingReader_fetchesOnePagePerSelect() throws Exception {
        QueryCountingDataSource counting = new QueryCountingDataSource(dataSource);
        JdbcPagingItemReader<Long> reader = buildPagingReader(counting);
        reader.open(new ExecutionContext());

        // pageSize 만큼 read → SELECT 는 단 1회만 발생 (한 페이지 메모리 버퍼에서 서빙)
        for (int i = 0; i < PAGE_SIZE; i++) {
            assertThat(reader.read()).isNotNull();
        }
        assertThat(counting.getQueryCount())
                .as("pageSize 건을 서빙하는 동안 SELECT 는 1회만 발생해야 함")
                .isEqualTo(1);

        // 한 건 더 read → 페이지 경계 초과 → 두 번째 SELECT 발생
        assertThat(reader.read()).isNotNull();
        assertThat(counting.getQueryCount())
                .as("페이지 경계 초과 시 다음 SELECT 가 발생해야 함")
                .isEqualTo(2);

        reader.close();
        log.info("[paging] pageSize={} 만큼 서빙 후 다음 read 에서 SELECT 발생 — 원리 확인", PAGE_SIZE);
    }

    // ──────────────────────────────────────────────────────────────────
    // ListItemReader — 현재 코드 방식
    // ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[paging 원리 확인] DB 조회 횟수 >= 처리 건수 / pageSize (페이지 단위 분할 로드 증명)")
    void jdbcPagingReader_queriesDb_atLeast_totalDividedByPageSize() throws Exception {
        QueryCountingDataSource counting = new QueryCountingDataSource(dataSource);
        JdbcPagingItemReader<Long> reader = buildPagingReader(counting);
        reader.open(new ExecutionContext());

        int count = 0;
        while (reader.read() != null) count++;
        reader.close();

        int expectedMin = count / PAGE_SIZE;
        assertThat(counting.getQueryCount())
                .as("pageSize=%d → 최소 %d번 SELECT 발생해야 함", PAGE_SIZE, expectedMin)
                .isGreaterThanOrEqualTo(expectedMin);

        log.info("[paging] 실제 SELECT 횟수={}, 예상 최소={} ({}건 / pageSize {})",
                counting.getQueryCount(), expectedMin, count, PAGE_SIZE);
    }

    // ──────────────────────────────────────────────────────────────────
    // Reader 빌더
    // ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[ListItemReader] PayrollStepConfig.payrollReader 패턴 — 전체 선적재 힙 스파이크")
    void listItemReader_payrollReader_causesHeapSpike() {
        long heapBefore = usedHeap();

        // 안티패턴 시연: memberId 목록 자체를 한 번에 끌어옴 (운영에서는 Reader 가 스트리밍해야 함)
        List<Long> memberIds = workRecordRepository.findDistinctMemberIdsByBizDateBetween(from, to);
        log.info("[ListItemReader] preload 시작: heap={}MB, 대상={}명", mb(heapBefore), memberIds.size());

        // PayrollStepConfig.payrollReader 와 동일한 1+3N 쿼리 패턴:
        //   memberIds 1회 + 멤버별 (workSchedule 1 + payrollPolicy 1 + workRecords 1) = 1+3N 쿼리
        // → 전체 PayrollInputDto 목록이 heap 에 올라오는 순간 OOM 위험
        try {
            List<PayrollInputDto> inputs = new ArrayList<>(memberIds.size());
            for (Long memberId : memberIds) {
                inputs.add(fetchPayrollInput(memberId));
            }
            new ListItemReader<>(inputs);

            long heapAfter = usedHeap();
            log.warn("[ListItemReader] OOM 미발생: heap {}MB → {}MB (+{}MB)",
                    mb(heapBefore), mb(heapAfter), mb(heapAfter - heapBefore));
            // OOM 이 나지 않더라도 전체 선적재 후 힙이 증가한 것을 확인
            assertThat(heapAfter)
                    .as("전체 선적재 후 힙이 증가해야 함")
                    .isGreaterThan(heapBefore);

        } catch (OutOfMemoryError oom) {
            // 예상된 동작 — 전체 선적재가 힙 한도를 초과 → 테스트 목적 달성
            log.error("[ListItemReader] OOM 발생 — 전체 선적재가 힙 한도를 초과함 (예상된 결과)");
        }
    }

    private JdbcCursorItemReader<Long> buildCursorReader() throws Exception {
        // MySQL 서버사이드 커서 사용:
        //   - JDBC URL 의 useCursorFetch=true 필수 (test application.yml 에 설정)
        //   - fetchSize 는 양수 — 서버에서 fetchSize 단위로 청크 전송 (커넥션 락 없음)
        JdbcCursorItemReader<Long> reader = new JdbcCursorItemReaderBuilder<Long>()
                .name("pocCursorReader")
                .dataSource(dataSource)
                .sql("SELECT DISTINCT member_id FROM work_record "
                        + "WHERE biz_date BETWEEN ? AND ? ORDER BY member_id")
                .fetchSize(PAGE_SIZE)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, from);
                    ps.setObject(2, to);
                })
                .rowMapper((rs, n) -> rs.getLong(1))
                .saveState(false)
                .build();
        reader.afterPropertiesSet();
        return reader;
    }

    /**
     * Spring Batch chunk-oriented 처리 시뮬레이션:
     * - read 한 PayrollInputDto 를 CHUNK_SIZE 까지 List 에 누적
     * - chunk 가 차면 flush(=write 시뮬레이션) 후 list.clear() → 그 chunk 는 GC 대상
     * 결과: heap 점유는 ~CHUNK_SIZE 만큼만 (톱니 패턴), 전체 건수는 무관하게 bounded
     */
    private int drainWithChunk(ItemReader<Long> reader, String tag) throws Exception {
        List<PayrollInputDto> chunk = new ArrayList<>(CHUNK_SIZE);
        int total = 0;
        int chunkNo = 0;

        Long memberId;
        while ((memberId = reader.read()) != null) {
            chunk.add(fetchPayrollInput(memberId));
            if (chunk.size() >= CHUNK_SIZE) {
                flushChunk(chunk, ++chunkNo, tag);
                total += chunk.size();
                chunk.clear();   // write 후 참조 해제 — 다음 chunk 적재 전 GC 대상
            }
        }
        // 마지막 잔여분 처리
        if (!chunk.isEmpty()) {
            flushChunk(chunk, ++chunkNo, tag);
            total += chunk.size();
            chunk.clear();
        }
        return total;
    }

    private void flushChunk(List<PayrollInputDto> chunk, int chunkNo, String tag) {
        // 실제 Step 에서는 Processor → Writer 가 동작하는 구간 — 여기서는 검증 + heap 로그만
        assertThat(chunk).isNotEmpty();
        if (chunkNo % 100 == 0) {   // 너무 자주 찍히면 노이즈 → 100 chunk 마다만 기록
            log.info("[{}] chunk#{} flush — chunkSize={}, heap={}MB",
                    tag, chunkNo, chunk.size(), mb(usedHeap()));
        }
    }

    /**
     * PayrollStepConfig.payrollReader 와 동일한 1+3N 패턴의 멤버별 부가 조회.
     * 한 건씩 만들고 즉시 버리면 heap 은 bounded — Reader 가 memberId 를 흘려보내는 한.
     */
    private PayrollInputDto fetchPayrollInput(Long memberId) {
        WorkScheduleProjection schedule = workScheduleRepository
                .findProjectionByMemberId(memberId).orElse(null);
        PayrollPolicyProjection policy = schedule != null
                ? payrollPolicyRepository.findProjectionByMemberId(memberId).orElse(null)
                : null;
        List<WorkRecordProjection> records = schedule != null
                ? workRecordRepository.findProjectionByMemberIdAndBizDateBetween(memberId, from, to)
                : List.of();
        YearMonth ym = YearMonth.parse(YEAR_MONTH);
        return new PayrollInputDto(memberId, ym.getYear(), ym.getMonthValue(), schedule, policy, records);
    }

    private JpaPagingItemReader<Long> buildJpaPagingReader() throws Exception {
        JpaPagingItemReader<Long> reader = new JpaPagingItemReaderBuilder<Long>()
                .name("pocJpaPagingReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT DISTINCT w.memberId FROM WorkRecord w "
                        + "WHERE w.bizDate BETWEEN :from AND :to ORDER BY w.memberId")
                .parameterValues(Map.of("from", from, "to", to))
                .pageSize(PAGE_SIZE)
                .saveState(false)
                .build();
        reader.afterPropertiesSet();
        return reader;
    }

    // ──────────────────────────────────────────────────────────────────
    // PreparedStatement 실행 횟수를 세는 DataSource 래퍼
    // ──────────────────────────────────────────────────────────────────

    private JdbcPagingItemReader<Long> buildPagingReader(DataSource ds) throws Exception {
        MySqlPagingQueryProvider qp = new MySqlPagingQueryProvider();
        qp.setSelectClause("DISTINCT member_id");
        qp.setFromClause("FROM work_record");
        qp.setWhereClause("biz_date BETWEEN :from AND :to");
        qp.setSortKeys(Map.of("member_id", Order.ASCENDING));

        JdbcPagingItemReader<Long> reader = new JdbcPagingItemReaderBuilder<Long>()
                .name("pocPagingReader")
                .dataSource(ds)
                .queryProvider(qp)
                .parameterValues(Map.of("from", from, "to", to))
                .pageSize(PAGE_SIZE)
                .rowMapper((rs, n) -> rs.getLong(1))
                .saveState(false)
                .build();
        reader.afterPropertiesSet();
        return reader;
    }

    // ──────────────────────────────────────────────────────────────────
    // 유틸
    // ──────────────────────────────────────────────────────────────────

    private long usedHeap() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        return mem.getHeapMemoryUsage().getUsed();
    }

    private static class QueryCountingDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicInteger queryCount = new AtomicInteger();

        QueryCountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        int getQueryCount() {
            return queryCount.get();
        }

        @Override
        public Connection getConnection() throws java.sql.SQLException {
            return wrapConnection(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String user, String pass) throws java.sql.SQLException {
            return wrapConnection(delegate.getConnection(user, pass));
        }

        private Connection wrapConnection(Connection conn) {
            return (Connection) Proxy.newProxyInstance(
                    conn.getClass().getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        Object result = method.invoke(conn, args);
                        if ("prepareStatement".equals(method.getName())) {
                            PreparedStatement ps = (PreparedStatement) result;
                            queryCount.incrementAndGet();
                            return ps;
                        }
                        return result;
                    });
        }

        // DataSource 위임 메서드
        @Override
        public java.io.PrintWriter getLogWriter() throws java.sql.SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter pw) throws java.sql.SQLException {
            delegate.setLogWriter(pw);
        }

        @Override
        public int getLoginTimeout() throws java.sql.SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public void setLoginTimeout(int s) throws java.sql.SQLException {
            delegate.setLoginTimeout(s);
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
