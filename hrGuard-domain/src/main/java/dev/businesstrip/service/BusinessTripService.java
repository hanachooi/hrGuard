package dev.businesstrip.service;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.businesstrip.entity.BusinessTrip;
import dev.businesstrip.exception.BusinessTripError;
import dev.businesstrip.exception.BusinessTripException;
import dev.businesstrip.repository.BusinessTripRepository;
import dev.workrecord.constant.WorkType;
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessTripService {

    // 출장 WorkRecord 기본 시작 시각 (WorkSchedule이 없을 때 폴백)
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);
    // 기본 일 근로시간 (8시간)
    private static final double DEFAULT_DAILY_HOURS = 8.0;

    private final BusinessTripRepository businessTripRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkScheduleRepository workScheduleRepository;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public BusinessTrip apply(Long memberId, LocalDate startDate, LocalDate endDate,
                              String destination, String purpose) {
        BusinessTrip trip = BusinessTrip.apply(memberId, startDate, endDate, destination, purpose);
        return businessTripRepository.save(trip);
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 출장을 승인하고 출장 기간 전체 날짜에 대해 WorkRecord를 생성합니다.
     *
     * <p>WorkSchedule이 등록된 사원은 소정 근로시간 기준으로 WorkRecord를 생성합니다.
     * 미등록 시 기본값(09:00 ~ 17:00, 8시간)을 사용합니다.</p>
     *
     * <p>출장은 소정 근무 요일 외 날짜도 WorkRecord를 생성합니다.
     * 배치 계산 시 해당 날짜가 휴일이면 TimeSegmentSplitter가 휴일 수당을 적용합니다.</p>
     */
    @Transactional
    public void approve(Long tripId) {
        BusinessTrip trip = findById(tripId);
        trip.approve();

        WorkSchedule schedule = workScheduleRepository.findByMemberId(trip.getMemberId())
                .orElse(null);

        LocalTime startTime = (schedule != null) ? schedule.getStartTime() : DEFAULT_START_TIME;
        LocalTime endTime = (schedule != null) ? schedule.getEndTime() : DEFAULT_START_TIME.plusMinutes((long) (DEFAULT_DAILY_HOURS * 60));

        List<WorkRecord> records = new ArrayList<>();
        LocalDate date = trip.getStartDate();
        while (!date.isAfter(trip.getEndDate())) {
            records.add(WorkRecord.builder()
                    .memberId(trip.getMemberId())
                    .bizDate(date)
                    .startTime(LocalDateTime.of(date, startTime))
                    .endTime(LocalDateTime.of(date, endTime))
                    .workType(WorkType.BUSINESS_TRIP)
                    .build());
            date = date.plusDays(1);
        }
        workRecordRepository.saveAll(records);
    }

    // ── 반려 ─────────────────────────────────────────────────────────────────

    @Transactional
    public void reject(Long tripId, String reason) {
        findById(tripId).reject(reason);
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BusinessTrip> findMyTrips(Long memberId) {
        return businessTripRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<BusinessTrip> findPending() {
        return businessTripRepository.findByStatusOrderByCreatedAtDesc(
                BusinessTripStatus.PENDING);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private BusinessTrip findById(Long id) {
        return businessTripRepository.findById(id)
                .orElseThrow(() -> new BusinessTripException(BusinessTripError.BUSINESS_TRIP_NOT_FOUND));
    }
}
