package dev.batch.sync.step;

import dev.commute.constant.CommuteStatus;
import dev.commute.entity.Commute;
import dev.commute.repository.CommuteRepository;
import dev.payroll.constant.WorkType;
import dev.payroll.entity.WorkRecord;
import dev.payroll.entity.WorkSchedule;
import dev.payroll.repository.WorkRecordRepository;
import dev.payroll.repository.WorkScheduleRepository;
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
 * Commute 세션 → WorkRecord(OFFICE) 변환 핵심 알고리즘.
 *
 * <h3>gap 보정 4가지</h3>
 * <pre>
 *  A. 퇴실 직후 blocked 시작 gap ≤ GAP_MERGE_MINUTES
 *     → effectiveEnd를 blocked 시작까지 확장 (조기 퇴실 오차 흡수)
 *     예) 퇴실 10:55 / FIELD 시작 11:00 → effectiveEnd=11:00
 *
 *  C. 입실 직전 blocked 종료 gap ≤ GAP_MERGE_MINUTES
 *     → effectiveStart를 blocked 종료로 당김 (복귀 지연 오차 흡수)
 *     예) BT 종료 14:00 / 입실 14:08 → effectiveStart=14:00
 *
 *  D-start. 입실이 workStartTime보다 GAP_MERGE_MINUTES 이내로 빠름
 *     → effectiveStart를 workStartTime으로 clamp (소규모 조기 출근 노이즈 제거)
 *     예) 08:52 입실 / workStart=09:00 / gap=8분 → effectiveStart=09:00
 *     단, gap > GAP_MERGE_MINUTES이면 초과근무로 간주, clamp 없이 유지
 *     예) 08:30 입실 / workStart=09:00 / gap=30분 → effectiveStart=08:30 (초과근무)
 *
 *  D-end. 퇴실이 workEndTime보다 GAP_MERGE_MINUTES 이내로 늦음
 *     → effectiveEnd를 workEndTime으로 clamp (소규모 잔업 노이즈 제거)
 *     예) 18:07 퇴실 / workEnd=18:00 / gap=7분 → effectiveEnd=18:00
 *     단, gap > GAP_MERGE_MINUTES이면 초과근무로 간주, clamp 없이 유지
 *     예) 19:00 퇴실 / workEnd=18:00 / gap=60분 → effectiveEnd=19:00 (초과근무)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommuteSyncProcessor {

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
     * 호출 전 기존 OFFICE 레코드 삭제는 StepConfig에서 처리한다(idempotency).
     */
    public List<WorkRecord> compute(Long memberId, LocalDate targetDate) {

        // ── 1. 완료된 Commute 세션 수집 ─────────────────────────────────────────
        List<Commute> sessions = commuteRepository
                .findByMemberIdAndWorkDateOrderByInTimeAsc(memberId, targetDate)
                .stream()
                .filter(c -> c.getStatus() == CommuteStatus.CHECKOUT)
                .filter(c -> c.getOutTime().isAfter(c.getInTime()))
                .toList();

        if (sessions.isEmpty()) {
            log.debug("완료된 출퇴근 세션 없음 skip: memberId={}, date={}", memberId, targetDate);
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

        // ── 3. WorkSchedule 조회 (D 보정에 사용, 없으면 clamp 생략) ──────────────
        WorkSchedule schedule = workScheduleRepository.findByMemberId(memberId).orElse(null);

        // ── 4. 세션별 effectiveWindow 산출 → OFFICE 슬롯 추출 ───────────────────
        List<WorkRecord> result = new ArrayList<>();
        for (Commute session : sessions) {

            // (D) 스케줄 기준 입실·퇴실 clamp (소규모 오차 → 정규 시작/종료로 정렬)
            LocalDateTime effectiveStart = clampToScheduleStart(session.getInTime(), schedule, targetDate);
            LocalDateTime effectiveEnd = clampToScheduleEnd(session.getOutTime(), schedule, targetDate);

            // (C) 입실 직전 blocked가 끝나는 경우 effectiveStart를 당겨 gap 흡수
            effectiveStart = absorbGapBeforeSession(effectiveStart, blocked);

            // (A) 퇴실 직후 blocked가 시작하는 경우 effectiveEnd를 늘려 gap 흡수
            effectiveEnd = absorbGapAfterSession(effectiveEnd, blocked);

            if (!effectiveEnd.isAfter(effectiveStart)) continue;

            result.addAll(buildOfficeSlots(memberId, targetDate, effectiveStart, effectiveEnd, blocked));
        }

        log.debug("OFFICE 슬롯 계산 완료: memberId={}, date={}, sessions={}, slots={}",
                memberId, targetDate, sessions.size(), result.size());
        return result;
    }

    // ── (A) 퇴실 직후 blocked 시작 gap 흡수 → effectiveEnd 확장 ────────────────

    private LocalDateTime absorbGapAfterSession(LocalDateTime end, List<Interval> blocked) {
        for (Interval block : blocked) {
            if (!block.start().isAfter(end)) continue;          // end 이전/동시 → 건너뜀
            long gap = Duration.between(end, block.start()).toMinutes();
            if (gap <= GAP_MERGE_MINUTES) return block.start(); // gap 흡수 → end 확장
            break;                                              // 정렬 보장, 첫 초과 시 종료
        }
        return end;
    }

    // ── (C) 입실 직전 blocked 종료 gap 흡수 → effectiveStart 당김 ───────────────

    private LocalDateTime absorbGapBeforeSession(LocalDateTime start, List<Interval> blocked) {
        // 역순 탐색: start 직전에 끝나는 가장 마지막 blocked 확인
        for (int i = blocked.size() - 1; i >= 0; i--) {
            Interval block = blocked.get(i);
            if (block.end().isAfter(start)) continue;               // start 이후에 끝남 → 건너뜀
            long gap = Duration.between(block.end(), start).toMinutes();
            return gap <= GAP_MERGE_MINUTES ? block.end() : start;  // gap 흡수 → start 당김
        }
        return start;
    }

    // ── (D) 스케줄 기준 입실 clamp ─────────────────────────────────────────────

    /**
     * 입실이 workStartTime보다 GAP_MERGE_MINUTES 이내로 빠르면 workStartTime으로 clamp.
     * gap > GAP_MERGE_MINUTES이면 초과근무로 간주, 원래 시각 유지.
     */
    private LocalDateTime clampToScheduleStart(LocalDateTime inTime, WorkSchedule schedule, LocalDate date) {
        if (schedule == null) return inTime;
        LocalDateTime workStart = LocalDateTime.of(date, schedule.getStartTime());
        if (!inTime.isBefore(workStart)) return inTime; // 정시/지각 → 그대로
        long gap = Duration.between(inTime, workStart).toMinutes();
        return gap <= GAP_MERGE_MINUTES ? workStart : inTime;
    }

    // ── (D) 스케줄 기준 퇴실 clamp ─────────────────────────────────────────────

    /**
     * 퇴실이 workEndTime보다 GAP_MERGE_MINUTES 이내로 늦으면 workEndTime으로 clamp.
     * gap > GAP_MERGE_MINUTES이면 초과근무로 간주, 원래 시각 유지.
     */
    private LocalDateTime clampToScheduleEnd(LocalDateTime outTime, WorkSchedule schedule, LocalDate date) {
        if (schedule == null) return outTime;
        LocalDateTime workEnd = LocalDateTime.of(date, schedule.getEndTime());
        if (!outTime.isAfter(workEnd)) return outTime; // 정시/조기퇴근 → 그대로
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
            if (!block.end().isAfter(cursor)) continue; // 이미 지난 블록 → 건너뜀
            if (!block.start().isBefore(end)) break;    // 범위 밖 블록 → 종료

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
