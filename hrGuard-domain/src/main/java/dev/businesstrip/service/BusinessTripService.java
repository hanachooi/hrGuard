package dev.businesstrip.service;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.businesstrip.entity.BusinessTrip;
import dev.businesstrip.exception.BusinessTripError;
import dev.businesstrip.exception.BusinessTripException;
import dev.businesstrip.repository.BusinessTripRepository;
import dev.workrecord.constant.WorkType;
import dev.workrecord.service.WorkRecordService;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.service.WorkScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessTripService {

    private final BusinessTripRepository businessTripRepository;
    private final WorkRecordService workRecordService;
    private final WorkScheduleService workScheduleService;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public BusinessTrip apply(Long memberId,
                              LocalDateTime startDateTime, LocalDateTime endDateTime,
                              String destination, String purpose) {
        return businessTripRepository.save(
                BusinessTrip.apply(memberId, startDateTime, endDateTime, destination, purpose));
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 출장을 승인하고 구간을 날짜별 WorkRecord 슬롯으로 분할하여 저장합니다.
     *
     * <p>슬롯 시간은 멤버의 WorkSchedule 기준이며, 없으면 기본값(09:00~18:00)을 사용합니다.</p>
     */
    @Transactional
    public void approve(Long tripId) {
        BusinessTrip trip = findById(tripId);
        trip.approve();

        WorkSchedule schedule = workScheduleService.findByMemberId(trip.getMemberId());
        workRecordService.registerApprovedSlots(
                trip.getMemberId(),
                trip.getStartDateTime(), trip.getEndDateTime(),
                schedule, WorkType.BUSINESS_TRIP);
    }

    // ── 반려 ─────────────────────────────────────────────────────────────────

    @Transactional
    public void reject(Long tripId, String reason) {
        findById(tripId).reject(reason);
    }

    // ── 취소 (승인 후 취소) ───────────────────────────────────────────────────

    @Transactional
    public void cancel(Long tripId) {
        BusinessTrip trip = findById(tripId);
        LocalDate date = trip.getStartDateTime().toLocalDate();
        LocalDate endDate = trip.getEndDateTime().toLocalDate();
        while (!date.isAfter(endDate)) {
            workRecordService.removeApprovedSlots(trip.getMemberId(), date, WorkType.BUSINESS_TRIP);
            date = date.plusDays(1);
        }
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BusinessTrip> findMyTrips(Long memberId) {
        return businessTripRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<BusinessTrip> findPending() {
        return businessTripRepository.findByStatusOrderByCreatedAtDesc(BusinessTripStatus.PENDING);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private BusinessTrip findById(Long id) {
        return businessTripRepository.findById(id)
                .orElseThrow(() -> new BusinessTripException(BusinessTripError.BUSINESS_TRIP_NOT_FOUND));
    }

}
