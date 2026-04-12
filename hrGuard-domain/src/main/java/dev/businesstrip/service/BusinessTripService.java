package dev.businesstrip.service;

import dev.businesstrip.entity.BusinessTrip;
import dev.businesstrip.exception.BusinessTripError;
import dev.businesstrip.exception.BusinessTripException;
import dev.businesstrip.repository.BusinessTripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessTripService {

    private final BusinessTripRepository businessTripRepository;

    // ── 신청 ─────────────────────────────────────────────────────────────────

    @Transactional
    public BusinessTrip apply(Long memberId, LocalDateTime startDateTime, LocalDateTime endDateTime,
                              String destination, String purpose) {
        return businessTripRepository.save(
                BusinessTrip.apply(memberId, startDateTime, endDateTime, destination, purpose));
    }

    // ── 승인 ─────────────────────────────────────────────────────────────────

    /**
     * 출장을 승인합니다.
     *
     * <p>BusinessTrip 상태를 APPROVED로 변경합니다.
     * WorkRecord 생성은 배치(WorkRecordComputeProcessor)가 담당합니다.</p>
     */
    @Transactional
    public void approve(Long tripId) {
        BusinessTrip trip = findById(tripId);
        trip.approve();
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
        return businessTripRepository.findPending();
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private BusinessTrip findById(Long id) {
        return businessTripRepository.findById(id)
                .orElseThrow(() -> new BusinessTripException(BusinessTripError.BUSINESS_TRIP_NOT_FOUND));
    }
}
