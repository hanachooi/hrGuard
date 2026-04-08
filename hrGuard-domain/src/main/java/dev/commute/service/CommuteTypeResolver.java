package dev.commute.service;

import dev.commute.constant.CommuteStatus;
import dev.commute.constant.CommuteType;
import dev.commute.repository.CommuteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommuteTypeResolver {

    private final CommuteRepository commuteRepository;

    // 출근 INSERT 커밋 완료 후 컨트롤러에서 호출
    // → 별도 스레드에서 commuteType 결정 후 UPDATE
    @Async("commuteTypeExecutor")
    @Transactional
    public void resolve(Long memberId, LocalDate workDate) {
        CommuteType type = determineType(workDate);

        commuteRepository.findByMemberIdAndWorkDateAndStatus(memberId, workDate, CommuteStatus.CHECKIN)
                .ifPresentOrElse(
                        commute -> {
                            commute.updateCommuteType(type);
                            log.debug("commuteType 결정 완료: memberId={}, workDate={}, type={}", memberId, workDate, type);
                        },
                        () -> log.warn("commuteType 결정 실패: 출근 기록 없음. memberId={}, workDate={}", memberId, workDate)
                );
    }

    // TODO: 공휴일 테이블, 휴가 신청 테이블 연동 시 로직 확장
    private CommuteType determineType(LocalDate workDate) {
        return CommuteType.WORK;
    }
}
