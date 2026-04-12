package dev.batch.WorkRecord.step;

import dev.businesstrip.entity.BusinessTrip;
import dev.businesstrip.repository.BusinessTripRepository;
import dev.commute.constant.CommuteStatus;
import dev.commute.entity.Commute;
import dev.commute.repository.CommuteRepository;
import dev.fieldwork.entity.FieldWork;
import dev.fieldwork.repository.FieldWorkRepository;
import dev.leave.entity.Leave;
import dev.leave.repository.LeaveRepository;
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.entity.WorkSchedule;
import dev.workschedule.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Commute + Leave + BusinessTrip + FieldWork → WorkRecord(일별 집계) 산출 핵심 알고리즘.
 *
 * <p>원천 데이터 전체를 읽어 시간 보정·차집합·수당 분류를 수행한 뒤,
 * {@code WorkRecord}에 분 단위 집계값을 저장합니다.
 * 매 실행 시 기존 레코드를 삭제하고 재생성하므로 멱등하게 실행됩니다.</p>
 *
 * <h3>처리 순서</h3>
 * <pre>
 *   1. Leave(휴가) 승인 → 날짜별 leaveInterval 산출 → leaveMinutes
 *   2. BusinessTrip(출장) 승인 → 소정 근무 시간 전체 → businessTripMinutes
 *   3. FieldWork(외근) 승인 → 신청 시간 그대로 → fieldWorkMinutes
 *   4. blocked = leave + businessTrip + fieldWork (시작 시각 오름차순)
 *   5. Commute 기반 gap 보정 + 차집합 → officeMinutes
 *   6. 야간 시간: OFFICE 구간 + FIELD 구간에서 22:00~06:00 교집합 → nightMinutes
 *   7. 소정 휴일 여부 판단 → OFFICE 구간 + FIELD 구간에서 정규/연장/휴일/휴일연장 분류 → 각 수당 minutes 저장
 * </pre>
 *
 * <h3>gap 보정 4종</h3>
 * <pre>
 *  A. 퇴실 직후 blocked 시작 gap ≤ GAP_MERGE_MINUTES → effectiveEnd 확장
 *  B. 입실 직전 blocked 종료 gap ≤ GAP_MERGE_MINUTES → effectiveStart 당김
 *  C-start. 입실이 workStart보다 ≤ GAP_MERGE_MINUTES 이내 빠름 → workStart로 clamp
 *  C-end. 퇴실이 workEnd보다 ≤ GAP_MERGE_MINUTES 이내 늦음 → workEnd로 clamp
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkRecordComputeProcessor {

    static final long GAP_MERGE_MINUTES = 15;
    static final long MIN_SLOT_MINUTES = 10;

    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);
    private static final double DEFAULT_DAILY_HOURS = 8.0;

    private final CommuteRepository commuteRepository;
    private final LeaveRepository leaveRepository;
    private final BusinessTripRepository businessTripRepository;
    private final FieldWorkRepository fieldWorkRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkScheduleRepository workScheduleRepository;

    /**
     * memberId 1명의 targetDate WorkRecord(집계)를 계산합니다.
     *
     * @return 집계 결과 WorkRecord, 활동 없으면 null (batch filter-out)
     */
    public WorkRecord compute(Long memberId, LocalDate targetDate) {

        // ── idempotency: 기존 집계 레코드 삭제 ─────────────────────────────────
        workRecordRepository.deleteByMemberIdAndBizDate(memberId, targetDate);

        // ── 근무 스케줄 조회 ────────────────────────────────────────────────────
        WorkSchedule schedule = workScheduleRepository.findByMemberId(memberId).orElse(null);
        LocalTime scheduleStart = schedule != null ? schedule.getStartTime() : DEFAULT_START;
        LocalTime scheduleEnd = schedule != null ? schedule.getEndTime() : DEFAULT_END;
        double dailyWorkHours = schedule != null ? schedule.getDailyWorkHours() : DEFAULT_DAILY_HOURS;
        Set<DayOfWeek> workDays = schedule != null
                ? schedule.getWorkDaysAsSet()
                : Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        // ── 1. Leave 승인 구간 ───────────────────────────────────────────────────
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime nextDay = targetDate.plusDays(1).atStartOfDay();
        List<Leave> leaves = leaveRepository.findApprovedByMemberIdAndDate(memberId, startOfDay, nextDay);
        List<Interval> leaveIntervals = leaves.stream()
                .flatMap(l -> clampToDate(l.getStartDateTime(), l.getEndDateTime(),
                        targetDate, scheduleStart, scheduleEnd).stream())
                .toList();
        int leaveMinutes = sumMinutes(leaveIntervals);

        // ── 2. BusinessTrip 승인 구간 (시작일/중간일/종료일 날짜 분할) ──────────────
        List<BusinessTrip> trips = businessTripRepository.findApprovedByMemberIdAndDate(memberId, startOfDay, nextDay);
        List<Interval> btIntervals = trips.stream()
                .flatMap(t -> clampToDate(t.getStartDateTime(), t.getEndDateTime(),
                        targetDate, scheduleStart, scheduleEnd).stream())
                .toList();
        int btMinutes = sumMinutes(btIntervals);

        // ── 3. FieldWork 승인 구간 (시작일/중간일/종료일 날짜 분할) ─────────────────
        List<FieldWork> fieldWorks = fieldWorkRepository.findApprovedByMemberIdAndWorkDate(memberId, startOfDay, nextDay);
        List<Interval> fwIntervals = fieldWorks.stream()
                .flatMap(f -> clampToDate(f.getStartDateTime(), f.getEndDateTime(),
                        targetDate, scheduleStart, scheduleEnd).stream())
                .toList();
        int fwMinutes = sumMinutes(fwIntervals);

        // ── 4. blocked = leave + BT + FW (오름차순 정렬) ─────────────────────────
        List<Interval> blocked = new ArrayList<>(leaveIntervals);
        blocked.addAll(btIntervals);
        blocked.addAll(fwIntervals);
        blocked.sort(Comparator.comparing(Interval::start));

        // ── 5. Commute → OFFICE 구간 (gap 보정 + 차집합) ─────────────────────────
        List<Commute> periods = commuteRepository
                .findByMemberIdAndWorkDateOrderByInTimeAsc(memberId, targetDate)
                .stream()
                .filter(c -> c.getStatus() == CommuteStatus.CHECKOUT)
                .filter(c -> c.getOutTime().isAfter(c.getInTime()))
                .toList();

        List<Interval> officeIntervals = new ArrayList<>();
        for (Commute period : periods) {
            LocalDateTime effectiveStart = clampToScheduleStart(period.getInTime(), scheduleStart, targetDate);
            LocalDateTime effectiveEnd = clampToScheduleEnd(period.getOutTime(), scheduleEnd, targetDate);
            effectiveStart = absorbGapBeforeSession(effectiveStart, blocked);
            effectiveEnd = absorbGapAfterSession(effectiveEnd, blocked);

            if (!effectiveEnd.isAfter(effectiveStart)) continue;

            officeIntervals.addAll(computeAvailableSlots(effectiveStart, effectiveEnd, blocked));
        }
        int officeMinutes = (int) officeIntervals.stream()
                .mapToLong(i -> Duration.between(i.start(), i.end()).toMinutes())
                .filter(m -> m >= MIN_SLOT_MINUTES)
                .sum();

        // ── 6. 야간 시간: OFFICE + FIELD 구간에서 22:00~06:00 교집합 ──────────────
        List<Interval> nightSources = new ArrayList<>(officeIntervals);
        nightSources.addAll(fwIntervals);
        int nightMinutes = (int) Math.round(
                nightSources.stream()
                        .mapToDouble(i -> calculateNightHours(i.start(), i.end()))
                        .sum() * 60.0);

        // ── 활동 없으면 skip ─────────────────────────────────────────────────────
        if (officeMinutes + leaveMinutes + btMinutes + fwMinutes == 0) {
            log.debug("활동 없음 skip: memberId={}, date={}", memberId, targetDate);
            return null;
        }

        // ── 7. 정산용 시간 분류 ─────────────────────────────────────────────────────
        boolean isHoliday = !workDays.contains(targetDate.getDayOfWeek());

        // 휴가는 법정 휴게시간 미차감 (근로기준법상 휴가는 유급으로 그대로 지급)
        double nonLeaveHours = deductBreakTime((officeMinutes + fwMinutes + btMinutes) / 60.0);
        double totalHours = nonLeaveHours + (leaveMinutes / 60.0);

        int regularMinutes, overtimeMinutes, holidayMinutes, holidayOvertimeMinutes;
        if (isHoliday) {
            holidayMinutes = toMinutes(Math.min(totalHours, dailyWorkHours));
            holidayOvertimeMinutes = toMinutes(Math.max(0, totalHours - dailyWorkHours));
            regularMinutes = 0;
            overtimeMinutes = 0;
        } else {
            regularMinutes = toMinutes(Math.min(totalHours, dailyWorkHours));
            overtimeMinutes = toMinutes(Math.max(0, totalHours - dailyWorkHours));
            holidayMinutes = 0;
            holidayOvertimeMinutes = 0;
        }

        log.debug("집계 완료: memberId={}, date={}, office={}m, leave={}m, bt={}m, fw={}m, regular={}m, overtime={}m, night={}m",
                memberId, targetDate, officeMinutes, leaveMinutes, btMinutes, fwMinutes,
                regularMinutes, overtimeMinutes, nightMinutes);

        return WorkRecord.builder()
                .memberId(memberId)
                .bizDate(targetDate)
                .officeMinutes(officeMinutes)
                .leaveMinutes(leaveMinutes)
                .businessTripMinutes(btMinutes)
                .fieldWorkMinutes(fwMinutes)
                .regularMinutes(regularMinutes)
                .overtimeMinutes(overtimeMinutes)
                .nightMinutes(nightMinutes)
                .holidayMinutes(holidayMinutes)
                .holidayOvertimeMinutes(holidayOvertimeMinutes)
                .build();
    }

    // ── 날짜별 구간 clamping ─────────────────────────────────────────────────────
    //
    // 다일(多日) 이벤트(휴가·출장·외근)를 targetDate 기준으로 잘라냅니다.
    //   - 시작일: max(entityStart, scheduleStart) ~ scheduleEnd
    //   - 중간일: scheduleStart ~ scheduleEnd (전체)
    //   - 종료일: scheduleStart ~ min(entityEnd, scheduleEnd)
    //   - 당일:   max(entityStart, scheduleStart) ~ min(entityEnd, scheduleEnd)
    //
    // 위 네 케이스 모두 max/min 두 줄로 자연스럽게 처리됩니다.

    private java.util.Optional<Interval> clampToDate(LocalDateTime entityStart, LocalDateTime entityEnd,
                                                     LocalDate date,
                                                     LocalTime scheduleStart, LocalTime scheduleEnd) {
        LocalDateTime sdtStart = date.atTime(scheduleStart);
        LocalDateTime sdtEnd = date.atTime(scheduleEnd);
        LocalDateTime start = entityStart.isBefore(sdtStart) ? sdtStart : entityStart;
        LocalDateTime end = entityEnd.isAfter(sdtEnd) ? sdtEnd : entityEnd;
        return end.isAfter(start)
                ? java.util.Optional.of(new Interval(start, end))
                : java.util.Optional.empty();
    }

    // ── gap 보정: (A) 퇴실 직후 blocked 시작 흡수 ────────────────────────────────

    private LocalDateTime absorbGapAfterSession(LocalDateTime end, List<Interval> blocked) {
        for (Interval block : blocked) {
            if (!block.start().isAfter(end)) continue;
            long gap = Duration.between(end, block.start()).toMinutes();
            if (gap <= GAP_MERGE_MINUTES) return block.start();
            break;
        }
        return end;
    }

    // ── gap 보정: (B) 입실 직전 blocked 종료 흡수 ────────────────────────────────

    private LocalDateTime absorbGapBeforeSession(LocalDateTime start, List<Interval> blocked) {
        for (int i = blocked.size() - 1; i >= 0; i--) {
            Interval block = blocked.get(i);
            if (block.end().isAfter(start)) continue;
            long gap = Duration.between(block.end(), start).toMinutes();
            return gap <= GAP_MERGE_MINUTES ? block.end() : start;
        }
        return start;
    }

    // ── gap 보정: (C-start) 입실 clamp ──────────────────────────────────────────

    private LocalDateTime clampToScheduleStart(LocalDateTime inTime, LocalTime scheduleStart, LocalDate date) {
        LocalDateTime workStart = LocalDateTime.of(date, scheduleStart);
        if (!inTime.isBefore(workStart)) return inTime;
        long gap = Duration.between(inTime, workStart).toMinutes();
        return gap <= GAP_MERGE_MINUTES ? workStart : inTime;
    }

    // ── gap 보정: (C-end) 퇴실 clamp ────────────────────────────────────────────

    private LocalDateTime clampToScheduleEnd(LocalDateTime outTime, LocalTime scheduleEnd, LocalDate date) {
        LocalDateTime workEnd = LocalDateTime.of(date, scheduleEnd);
        if (!outTime.isAfter(workEnd)) return outTime;
        long gap = Duration.between(workEnd, outTime).toMinutes();
        return gap <= GAP_MERGE_MINUTES ? workEnd : outTime;
    }

    // ── cursor 전진 방식 빈 구간 계산 ────────────────────────────────────────────

    private List<Interval> computeAvailableSlots(LocalDateTime start, LocalDateTime end,
                                                 List<Interval> blocked) {
        List<Interval> available = new ArrayList<>();
        LocalDateTime cursor = start;

        for (Interval block : blocked) {
            if (!block.end().isAfter(cursor)) continue;
            if (!block.start().isBefore(end)) break;

            if (block.start().isAfter(cursor)) {
                available.add(new Interval(cursor, block.start()));
            }
            cursor = block.end().isBefore(end) ? block.end() : end;
        }

        if (cursor.isBefore(end)) {
            available.add(new Interval(cursor, end));
        }
        return available;
    }

    // ── 야간 시간 계산 (22:00~06:00 교집합) ──────────────────────────────────────

    private double calculateNightHours(LocalDateTime inTime, LocalDateTime outTime) {
        double nightMinutes = 0;
        LocalDate date = inTime.toLocalDate().minusDays(1);
        LocalDate endDate = outTime.toLocalDate();

        while (!date.isAfter(endDate)) {
            LocalDateTime nightStart = date.atTime(22, 0);
            LocalDateTime nightEnd = date.plusDays(1).atTime(6, 0);

            LocalDateTime overlapStart = inTime.isAfter(nightStart) ? inTime : nightStart;
            LocalDateTime overlapEnd = outTime.isBefore(nightEnd) ? outTime : nightEnd;

            if (overlapStart.isBefore(overlapEnd)) {
                nightMinutes += Duration.between(overlapStart, overlapEnd).toMinutes();
            }
            date = date.plusDays(1);
        }
        return nightMinutes / 60.0;
    }

    // ── 근로기준법 제54조 휴게시간 차감 ─────────────────────────────────────────

    private double deductBreakTime(double rawHours) {
        if (rawHours >= 8.0) return rawHours - 1.0;
        if (rawHours >= 4.0) return rawHours - 0.5;
        return rawHours;
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────────

    private int sumMinutes(List<Interval> intervals) {
        return (int) intervals.stream()
                .mapToLong(i -> Duration.between(i.start(), i.end()).toMinutes())
                .sum();
    }

    private int toMinutes(double hours) {
        return (int) Math.round(hours * 60.0);
    }

    record Interval(LocalDateTime start, LocalDateTime end) {
    }
}
