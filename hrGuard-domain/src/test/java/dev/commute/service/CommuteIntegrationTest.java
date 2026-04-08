package dev.commute.service;

import dev.commute.constant.CommuteStatus;
import dev.commute.entity.Commute;
import dev.commute.exception.CommuteError;
import dev.commute.exception.CommuteException;
import dev.commute.repository.CommuteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommuteIntegrationTest {

    private static final Long MEMBER_ID = 999L;
    @Autowired
    private CommuteService commuteService;
    @Autowired
    private CommuteRepository commuteRepository;

    @AfterEach
    void tearDown() {
        commuteRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("정상 출근: CHECKIN 레코드가 DB에 저장된다")
    void 정상출근() {
        // given
        LocalDate today = LocalDate.now();

        // when
        commuteService.checkIn(MEMBER_ID);

        // then
        Commute saved = commuteRepository
                .findByMemberIdAndWorkDateAndStatus(MEMBER_ID, today, CommuteStatus.CHECKIN)
                .orElseThrow(() -> new AssertionError("CHECKIN 레코드가 존재하지 않습니다"));

        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getWorkDate()).isEqualTo(today);
        assertThat(saved.getStatus()).isEqualTo(CommuteStatus.CHECKIN);
        assertThat(saved.getInTime()).isNotNull();
        assertThat(saved.getOutTime()).isNull();
    }

    @Test
    @DisplayName("중복 출근: 이미 출근 중이면 예외가 발생하고 DB에 추가 저장되지 않는다")
    void 중복출근() {
        // given
        Commute existing = Commute.builder()
                .memberId(MEMBER_ID)
                .workDate(LocalDate.now())
                .status(CommuteStatus.CHECKIN)
                .inTime(LocalDateTime.now().minusHours(1))
                .build();
        commuteRepository.save(existing);

        // when & then
        assertThatThrownBy(() -> commuteService.checkIn(MEMBER_ID))
                .isInstanceOf(CommuteException.class)
                .hasMessageContaining(CommuteError.COMMUTE_ALREADY.getMessage());
        ;

        List<Commute> records = commuteRepository
                .findByMemberIdAndWorkDateOrderByInTimeAsc(MEMBER_ID, LocalDate.now());
        assertThat(records).hasSize(1);
    }
}
