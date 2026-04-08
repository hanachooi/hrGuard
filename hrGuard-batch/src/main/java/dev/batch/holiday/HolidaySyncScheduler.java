package dev.batch.holiday;

import dev.batch.holiday.client.HolidayApiClient;
import dev.batch.holiday.client.HolidayItem;
import dev.holiday.entity.Holiday;
import dev.holiday.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// 매년 1월 1일 01:00 KST에 해당 연도 공휴일 전체 동기화
// 대체공휴일 추가 고시 등 필요 시 syncYear() 직접 호출
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidaySyncScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HolidayApiClient holidayApiClient;
    private final HolidayRepository holidayRepository;

    @Scheduled(cron = "0 0 1 1 1 *", zone = "Asia/Seoul")
    public void syncCurrentYear() {
        int year = LocalDate.now(KST).getYear();
        syncYear(year);
    }

    public void syncYear(int year) {
        int saved = 0;
        for (int month = 1; month <= 12; month++) {
            try {
                List<HolidayItem> holidays = holidayApiClient.fetchHolidays(year, month);
                for (HolidayItem item : holidays) {
                    if (!holidayRepository.existsByDate(item.date())) {
                        holidayRepository.save(Holiday.builder().date(item.date()).name(item.name()).build());
                        saved++;
                    }
                }
            } catch (Exception e) {
                log.warn("{}년 {}월 공휴일 조회 실패: {}", year, month, e.getMessage());
            }
        }
        log.info("공휴일 동기화 완료: {}년, 신규={}건", year, saved);
    }
}
