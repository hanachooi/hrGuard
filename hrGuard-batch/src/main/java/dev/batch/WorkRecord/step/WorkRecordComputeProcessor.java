package dev.batch.WorkRecord.step;

import dev.commute.constant.CommuteStatus;
import dev.commute.entity.Commute;
import dev.commute.repository.CommuteRepository;
import dev.workrecord.constant.WorkType;
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commute 기간 → WorkRecord(OFFICE) 산출 핵심 알고리즘.
 *
 * <p>idempotency 처리(기존 OFFICE 레코드 삭제)와 슬롯 계산을 함께 담당합니다.
 * 승인 기반 레코드(FIELD, BUSINESS_TRIP, ANNUAL_LEAVE)는 workType 조건으로 보호됩니다.</p>
 *
 * <h3>gap 보정 4가지</h3>
 * <pre>
 *  A. 퇴실 직후 blocked 시작 gap ≤ GAP_MERGE_MINUTES
 *     → effectiveEnd를 blocked 시작까지 확장 (조기 퇴실 오차 흡수)
 *     예) 퇴실 10:55 / FIELD 시작 11:00 → effectiveEnd=11:00
 *
 *  B. 입실 직전 blocked 종료 gap ≤ GAP_MERGE_MINUTES
 *     → effectiveStart를 blocked 종료로 당김 (복귀 지연 오차 흡수)
 *     예) BT 종료 14:00 / 입실 14:08 → effectiveStart=14:00
 *
 *  C-start. 입실이 workStartTime보다 GAP_MERGE_MINUTES 이내로 빠름
 *     → effectiveStart를 workStartTime으로 clamp (소규모 조기 출근 노이즈 제거)
 *     예) 08:52 입실 / workStart=09:00 / gap=8분 → effectiveStart=09:00
 *     단, gap > GAP_MERGE_MINUTES이면 초과근무로 간주, clamp 없이 유지
 *     예) 08:30 입실 / workStart=09:00 / gap=30분 → effectiveStart=08:30 (초과근무)
 *
 *  C-end. 퇴실이 workEndTime보다 GAP_MERGE_MINUTES 이내로 늦음
 *     → effectiveEnd를 workEndTime으로 clamp (소규모 잔업 노이즈 제거)
 *     예) 18:07 퇴실 / workEnd=18:00 / gap=7분 → effectiveEnd=18:00
 *     단, gap > GAP_MERGE_MINUTES이면 초과근무로 간주, clamp 없이 유지
 *     예) 19:00 퇴실 / workEnd=18:00 / gap=60분 → effectiveEnd=19:00 (초과근무)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkRecordComputeProcessor {

    /**
     * 입실·퇴실 오차 흡수 기준 (분). 이 이하 gap은 보정 대상.
     */
    static final long GAP_MERGE_MINUTES = 15;

    /**
     * 이 미만 슬롯은 노이즈로 간주, OFFICE WorkRecord 생성 안 함
     */
    static final long MIN_SLOT_MINUTES = 10;

    private final CommuteRepository commuteRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkScheduleRepository workScheduleRepository;

    /**
     * memberId 1명의 targetDate OFFICE WorkRecord 목록을 계산한다.
     *
     * <p>기존 OFFICE 레코드를 먼저 삭제한 뒤 재생성하므로 멱등하게 실행됩니다.
     * 슬롯이 없으면 빈 리스트를 반환합니다.</p>
     */
    public List<WorkRecord> compute(Long memberId, LocalDate targetDate) {

        // ── idempotency: 기존 OFFICE 레코드 삭제 후 재생성 ──────────────────────
        workRecordRepository.deleteByMemberIdAndBizDateAndWorkType(
                memberId, targetDate, WorkType.OFFICE);

        // ── 1. 완료된 Commute 기간 수집 ─────────────────────────────────────────
        List<Commute> periods = commuteRepository
                .findByMemberIdAndWorkDateOrderByInTimeAsc(memberId, targetDate)
                .stream()
                .filter(c -> c.getStatus() == CommuteStatus.CHECKOUT)
                .filter(c -> c.getOutTime().isAfter(c.getInTime()))
                .toList();

        if (periods.isEmpty()) {
            log.debug("완료된 출퇴근 기간 없음 skip: memberId={}, date={}", memberId, targetDate);
            return List.of();
        }

        // ── 2. 승인 기반 blocked 구간 수집 (OFFICE 제외, 시작 시각 오름차순) ──────
        List<Interval> blocked = workRecordRepository
                .findByMemberIdAndBizDateOrderByStartTimeAsc(memberId, targetDate)
                .stream()
                .filter(r -> r.getWorkType() != WorkType.OFFICE)
                .filter(r -> r.getEndTime() != null)
                .map(r -> new Interval(r.getStartTime(), r.getEndTime()))
                .collect(Collectors.toCollection(ArrayList::new));

        // ── 3. WorkSchedule 조회 (C 보정에 사용, 없으면 clamp 생략) ──────────────
        WorkSchedule schedule = workScheduleRepository.findByMemberId(memberId).orElse(null);

        // ── 4. 기간별 effectiveWindow 산출 → OFFICE 슬롯 추출 ───────────────────
        List<WorkRecord> result = new ArrayList<>();
        for (Commute period : periods) {

            LocalDateTime effectiveStart = clampToScheduleStart(period.getInTime(), schedule, targetDate);
            LocalDateTime effectiveEnd = clampToScheduleEnd(period.getOutTime(), schedule, targetDate);

            effectiveStart = absorbGapBeforeSession(effectiveStart, blocked);
            effectiveEnd = absorbGapAfterSession(effectiveEnd, blocked);

            if (!effectiveEnd.isAfter(effectiveStart)) continue;

            result.addAll(buildOfficeSlots(memberId, targetDate, effectiveStart, effectiveEnd, blocked));
        }

        log.debug("OFFICE 슬롯 산출 완료: memberId={}, date={}, periods={}, slots={}",
                memberId, targetDate, periods.size(), result.size());
        return result;
    }

    // ── (A) 퇴실 직후 blocked 시작 gap 흡수 → effectiveEnd 확장 ────────────────

    private LocalDateTime absorbGapAfterSession(LocalDateTime end, List<Interval> blocked) {
        for (Interval block : blocked) {
            if (!block.start().isAfter(end)) continue;
            long gap = Duration.between(end, block.start()).toMinutes();
            if (gap <= GAP_MERGE_MINUTES) return block.start();
            break;
        }
        return end;
    }

    // ── (B) 입실 직전 blocked 종료 gap 흡수 → effectiveStart 당김 ───────────────

    private LocalDateTime absorbGapBeforeSession(LocalDateTime start, List<Interval> blocked) {
        for (int i = blocked.size() - 1; i >= 0; i--) {
            Interval block = blocked.get(i);
            if (block.end().isAfter(start)) continue;
            long gap = Duration.between(block.end(), start).toMinutes();
            return gap <= GAP_MERGE_MINUTES ? block.end() : start;
        }
        return start;
    }

    // ── (C) 스케줄 기준 입실 clamp ─────────────────────────────────────────────

    private LocalDateTime clampToScheduleStart(LocalDateTime inTime, WorkSchedule schedule, LocalDate date) {
        if (schedule == null) return inTime;
        LocalDateTime workStart = LocalDateTime.of(date, schedule.getStartTime());
        if (!inTime.isBefore(workStart)) return inTime;
        long gap = Duration.between(inTime, workStart).toMinutes();
        return gap <= GAP_MERGE_MINUTES ? workStart : inTime;
    }

    // ── (C) 스케줄 기준 퇴실 clamp ─────────────────────────────────────────────

    private LocalDateTime clampToScheduleEnd(LocalDateTime outTime, WorkSchedule schedule, LocalDate date) {
        if (schedule == null) return outTime;
        LocalDateTime workEnd = LocalDateTime.of(date, schedule.getEndTime());
        if (!outTime.isAfter(workEnd)) return outTime;
        long gap = Duration.between(workEnd, outTime).toMinutes();
        return gap <= GAP_MERGE_MINUTES ? workEnd : outTime;
    }

    // ── cursor 전진 방식 빈 슬롯 → OFFICE WorkRecord 생성 ────────────────────────

    private List<WorkRecord> buildOfficeSlots(Long memberId, LocalDate workDate,
                                              LocalDateTime start, LocalDateTime end,
                                              List<Interval> blocked) {
        List<WorkRecord> result = new ArrayList<>();
        LocalDateTime cursor = start;

        for (Interval block : blocked) {
            if (!block.end().isAfter(cursor)) continue;
            if (!block.start().isBefore(end)) break;

            addIfValid(result, memberId, workDate, cursor, block.start());
            cursor = block.end().isBefore(end) ? block.end() : end;
        }
        addIfValid(result, memberId, workDate, cursor, end);
        return result;
    }

    private void addIfValid(List<WorkRecord> target, Long memberId, LocalDate workDate,
                            LocalDateTime slotStart, LocalDateTime slotEnd) {
        if (!slotEnd.isAfter(slotStart)) return;
        if (Duration.between(slotStart, slotEnd).toMinutes() < MIN_SLOT_MINUTES) {
            log.debug("최소 슬롯 미달 무시: memberId={}, {}~{}", memberId, slotStart, slotEnd);
            return;
        }
        target.add(WorkRecord.builder()
                .memberId(memberId)
                .bizDate(workDate)
                .startTime(slotStart)
                .endTime(slotEnd)
                .workType(WorkType.OFFICE)
                .build());
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {
    }
}
