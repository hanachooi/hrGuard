package dev.payroll.service;

import dev.payroll.constant.WorkType;
import dev.payroll.entity.WorkRecord;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * inTime ~ outTime 구간을 근로기준법 기준으로 분해합니다.
 *
 * <h3>휴일 판단</h3>
 * 개인 WorkSchedule의 소정 근무 요일({@code scheduledWorkDays})만을 기준으로 판단합니다.
 * <ul>
 *   <li>출근일의 요일이 {@code scheduledWorkDays}에 포함되면 → 소정 근무일 (정규/연장 계산)</li>
 *   <li>포함되지 않으면 → 소정 휴일 근무 (휴일 가산율 적용)</li>
 * </ul>
 *
 * <h3>야간 수당</h3>
 * 22:00~06:00 구간과 실제 근무 구간의 교집합을 계산.
 * 정규/연장/휴일 여부와 무관하게 중복 적용됩니다.
 */
@Component
public class TimeSegmentSplitter {

    /**
     * @param inTime            출근 시각
     * @param outTime           퇴근 시각
     * @param scheduledWorkDays 소정 근무 요일 Set (WorkSchedule.getWorkDaysAsSet())
     * @param dailyWorkHours    일 소정 근로시간 (WorkSchedule.dailyWorkHours, 기본 8.0h)
     */
    public WorkTimeResult split(
            LocalDateTime inTime,
            LocalDateTime outTime,
            Set<DayOfWeek> scheduledWorkDays,
            double dailyWorkHours
    ) {
        double rawHours = Duration.between(inTime, outTime).toMinutes() / 60.0;
        double totalHours = deductBreakTime(rawHours); // 근로기준법 휴게시간 차감
        double nightHours = calculateNightHours(inTime, outTime);

        boolean isHolidayWork = !scheduledWorkDays.contains(inTime.getDayOfWeek());

        if (isHolidayWork) {
            double holidayRegular = Math.min(totalHours, dailyWorkHours);
            double holidayOvertime = Math.max(0, totalHours - dailyWorkHours);
            return new WorkTimeResult(0, 0, nightHours, holidayRegular, holidayOvertime);
        } else {
            double regular = Math.min(totalHours, dailyWorkHours);
            double overtime = Math.max(0, totalHours - dailyWorkHours);
            return new WorkTimeResult(regular, overtime, nightHours, 0, 0);
        }
    }

    /**
     * 하루에 여러 근무 세그먼트(외근·출장 등)가 있을 때 일별 합산 후 계산합니다.
     *
     * <p>기존 {@code split()}은 단일 출퇴근 1건을 처리하지만,
     * 이 메서드는 같은 {@code workDate}의 여러 {@link WorkRecord}를 받아
     * 연장·야간·휴일 수당을 정확히 계산합니다.</p>
     *
     * <h3>핵심 원칙</h3>
     * <ul>
     *   <li>야간 시간: 세그먼트별 독립 계산 후 합산 (시간대 기반이므로 분리 필요)</li>
     *   <li>연장 시간: 하루 총 근무시간 합산 후 {@code dailyWorkHours} 초과분으로 판단</li>
     *   <li>휴일 판단: {@code workDate} 요일 기준 (세그먼트 중 첫 번째가 아닌 날짜 자체)</li>
     *   <li>endTime == null 세그먼트: 퇴근 미기록으로 계산에서 제외</li>
     * </ul>
     *
     * @param bizDate           근무 날짜 (장부 기준)
     * @param segments          해당 날짜의 근무 기록 목록 (하루 1건~N건)
     * @param scheduledWorkDays 소정 근무 요일 Set
     * @param dailyWorkHours    일 소정 근로시간
     */
    public WorkTimeResult splitDaily(
            LocalDate bizDate,
            List<WorkRecord> segments,
            Set<DayOfWeek> scheduledWorkDays,
            double dailyWorkHours
    ) {
        // ── endTime 없는 세그먼트 제외 ────────────────────────────────────
        List<WorkRecord> validSegments = segments.stream()
                .filter(s -> s.getEndTime() != null)
                .toList();

        if (validSegments.isEmpty()) {
            return new WorkTimeResult(0, 0, 0, 0, 0);
        }

        // ── 야간 시간: 세그먼트별 계산 후 합산 ───────────────────────────
        double totalNightHours = validSegments.stream()
                .mapToDouble(s -> calculateNightHours(s.getStartTime(), s.getEndTime()))
                .sum();

        // ── 총 근무 시간: 세그먼트별 합산 후 일별 휴게시간 차감 ──────────
        // ANNUAL_LEAVE 유형: 휴가 승인일수만큼 그대로 지급 (법정 휴게시간 불필요)
        // 일반 근무 유형: 일 합산 기준으로 휴게시간 1회 차감
        double leaveHours = validSegments.stream()
                .filter(s -> s.getWorkType() == WorkType.ANNUAL_LEAVE)
                .mapToDouble(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0)
                .sum();
        double rawWorkHours = validSegments.stream()
                .filter(s -> s.getWorkType() != WorkType.ANNUAL_LEAVE)
                .mapToDouble(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0)
                .sum();
        double totalHours = leaveHours + deductBreakTime(rawWorkHours);

        // ── 휴일 판단: biz_date 요일 기준 ────────────────────────────────
        boolean isHolidayWork = !scheduledWorkDays.contains(bizDate.getDayOfWeek());

        if (isHolidayWork) {
            double holidayRegular = Math.min(totalHours, dailyWorkHours);
            double holidayOvertime = Math.max(0, totalHours - dailyWorkHours);
            return new WorkTimeResult(0, 0, totalNightHours, holidayRegular, holidayOvertime);
        } else {
            double regular = Math.min(totalHours, dailyWorkHours);
            double overtime = Math.max(0, totalHours - dailyWorkHours);
            return new WorkTimeResult(regular, overtime, totalNightHours, 0, 0);
        }
    }

    /**
     * 근로기준법 제54조 — 휴게시간 차감.
     *
     * <ul>
     *   <li>4시간 미만 : 휴게 없음</li>
     *   <li>4시간 이상 : 30분 차감</li>
     *   <li>8시간 이상 : 1시간 차감</li>
     * </ul>
     *
     * <p>세그먼트 간 공백(외근 이동, 점심 등)은 이미 근무시간에 미포함이므로
     * 이 메서드는 일 합산 기준으로 한 번만 호출합니다.</p>
     */
    private double deductBreakTime(double rawHours) {
        if (rawHours >= 8.0) return rawHours - 1.0;
        if (rawHours >= 4.0) return rawHours - 0.5;
        return rawHours;
    }

    /**
     * 야간 구간(22:00~06:00)과 실제 근무 구간의 교집합 시간을 계산합니다.
     * 자정을 넘기는 교대 근무도 날짜별로 순회해 정확히 처리합니다.
     */
    private double calculateNightHours(LocalDateTime inTime, LocalDateTime outTime) {
        double nightMinutes = 0;

        // 전날 22:00부터 시작하는 야간 구간도 체크
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
}
