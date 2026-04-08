package dev.workschedule.entity;

import dev.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 개인별 주간 소정 근무 일정표.
 *
 * <p>TimeSegmentSplitter 의 휴일 판단 로직에서
 * "법정 공휴일이 아니더라도 소정 근무 요일이 아닌 날"을 휴일 근무로 처리하기 위해 사용합니다.</p>
 *
 * <p>휴일 판단 우선순위:</p>
 * <ol>
 *   <li>holiday 테이블에 있는 법정 공휴일 → 휴일 가산 적용</li>
 *   <li>workDays에 포함되지 않는 요일 (소정 휴일) → 휴일 가산 적용</li>
 *   <li>위 둘 다 아닌 경우 → 정규/연장 계산</li>
 * </ol>
 *
 * <p>workDays 컬럼 형식: 쉼표 구분 DayOfWeek 이름</p>
 * <pre>
 *   예시) "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"  → 주 5일 근무
 *        "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY" → 주 6일 근무
 * </pre>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "work_schedule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id"}))
public class WorkSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대상 회원 ID (1인 1스케줄)
     */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * 소정 근무 요일 (쉼표 구분, DayOfWeek 이름 기준).
     * ex) "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
     */
    @Column(name = "work_days", nullable = false, length = 100)
    private String workDays;

    /**
     * 소정 근무 시작 시각 (기본 09:00).
     * CommuteSyncProcessor 의 입실 clamp 기준.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * 소정 근무 종료 시각 (기본 18:00).
     * startTime + dailyWorkHours 로 계산하지 않는 이유:
     * dailyWorkHours 는 실제 근로시간(휴식 차감 후)이므로
     * 09:00 + 8h = 17:00 이 되어 실제 퇴근 시각(18:00)과 어긋남.
     */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * 일 소정 근로시간.
     * 연장 근무 기준선으로 사용됨 (기본 8.0시간, 휴식 차감 후 순수 근로시간).
     */
    @Column(name = "daily_work_hours", nullable = false)
    private double dailyWorkHours;

    /**
     * 시급 (원). WorkContract 제거 후 이 필드로 일원화.
     */
    @Column(name = "hourly_wage", nullable = false)
    private int hourlyWage;

    /**
     * workDays 문자열 → {@link DayOfWeek} Set 변환.
     * TimeSegmentSplitter 에서 소정 근무일 여부 판단에 사용.
     */
    public Set<DayOfWeek> getWorkDaysAsSet() {
        return Arrays.stream(workDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }
}
