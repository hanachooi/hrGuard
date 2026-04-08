package dev.commute.service;

import dev.commute.constant.CommuteStatus;
import dev.commute.entity.Commute;
import dev.commute.exception.CommuteError;
import dev.commute.exception.CommuteException;
import dev.commute.repository.CommuteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CommuteService {

    private final CommuteRepository commuteRepository;

    /**
     * 출근 처리.
     *
     * <p>하루에 여러 번 출근 가능 (외근 후 복귀 등). 단, 이미 열린 세션(퇴근 미완료)이
     * 존재하면 차단한다.</p>
     *
     * <p>동시 출근 요청에 대한 방어는 애플리케이션 레벨 존재 여부 체크로 처리.
     * 출입 단말은 물리적으로 한 번에 한 명만 처리하므로 동시 요청 가능성이 극히 낮다.</p>
     */
    @Transactional
    public void checkIn(Long memberId) {
        LocalDate today = LocalDate.now();

        if (commuteRepository.existsByMemberIdAndWorkDateAndStatus(memberId, today, CommuteStatus.CHECKIN)) {
            throw new CommuteException(CommuteError.COMMUTE_ALREADY);
        }

        commuteRepository.save(Commute.checkIn(memberId));
    }

    /**
     * 퇴근 처리.
     *
     * <p>현재 열린 세션(status = CHECKIN)을 찾아 퇴근 시각을 기록한다.
     * 이후 다시 출근하면 새로운 Commute 세션이 생성된다.</p>
     */
    @Transactional
    public void checkOut(Long memberId) {
        LocalDate today = LocalDate.now();

        Commute commute = commuteRepository
                .findByMemberIdAndWorkDateAndStatus(memberId, today, CommuteStatus.CHECKIN)
                .orElseThrow(() -> new CommuteException(CommuteError.COMMUTE_NOT_FOUND));

        commute.checkOut();
    }
}
