package dev.workrecord.service;

import dev.workrecord.constant.WorkType;
import dev.workrecord.entity.WorkRecord;
import dev.workrecord.entity.WorkSlot;
import dev.workrecord.exception.WorkRecordError;
import dev.workrecord.exception.WorkRecordException;
import dev.workrecord.repository.WorkRecordRepository;
import dev.workschedule.entity.WorkSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkRecord 슬롯 관리 서비스 — 모든 근무 기록 변경의 단일 진입점.
 *
 * <h3>승인 기반 슬롯 등록 (알박기 방식)</h3>
 * <p>외근·출장·재택·휴가 승인 시 해당 시간대 슬롯을 즉시 점유합니다.
 * 이미 점유된 시간대와 겹치는 신청은 {@link WorkRecordError#SLOT_CONFLICT} 예외로 거부합니다.</p>
 *
 * <h3>OFFICE 슬롯 산출 (배치 전용)</h3>
 * <p>{@link #clearOfficeSlots}로 기존 OFFICE 슬롯을 초기화한 뒤,
 * {@code WorkRecordComputeProcessor} 가 {@link #computeAvailableSlots} 의 차집합 결과를
 * 바탕으로 빈 슬롯을 채웁니다.</p>
 *
 * <h3>10분 슬롯 단위</h3>
 * <p>하루 144슬롯(24h × 6) 논리를 기반으로 하며,
 * MIN_SLOT_MINUTES 미만의 구간은 Processor 에서 노이즈로 제거합니다.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WorkRecordService {

    private final WorkRecordRepository workRecordRepository;

    // ── 승인 기반 슬롯 등록 ──────────────────────────────────────────────────────

    /**
     * 특정 유효 구간[start, end)에서 blocked 슬롯을 제외한 빈 슬롯 목록을 반환합니다.
     *
     * <p>이 메서드가 WorkRecordComputeProcessor 의 핵심 알고리즘입니다.
     * Commute 기간에서 승인 기반 슬롯(OFFICE 제외)을 뺀 나머지 구간이
     * OFFICE 슬롯 후보가 됩니다.</p>
     *
     * <h3>알고리즘 (cursor 전진)</h3>
     * <pre>
     *   cursor = start
     *   for each block in blocked (startTime 오름차순):
     *     ① block 이 cursor 이전에 끝남 → skip
     *     ② block 시작이 구간 밖 → break
     *     ③ cursor ~ block.start 사이 빈 구간 추가
     *     ④ cursor = min(block.end, end)
     *   남은 구간 cursor ~ end 추가
     * </pre>
     *
     * <h3>예시</h3>
     * <pre>
     *   유효 구간 : 09:00 ~ 18:00
     *   blocked  : [11:00~12:00 FIELD], [14:00~15:00 BT]
     *   결과     : [09:00~11:00], [12:00~14:00], [15:00~18:00]
     * </pre>
     *
     * @param start   유효 시작 (Commute effectiveStart 보정 후)
     * @param end     유효 종료 (Commute effectiveEnd 보정 후)
     * @param blocked 점유 구간 목록 (startTime 오름차순 정렬 필수)
     * @return 빈 슬롯 구간 목록
     */
    public static List<TimeInterval> computeAvailableSlots(
            LocalDateTime start, LocalDateTime end, List<WorkSlot> blocked) {

        List<TimeInterval> available = new ArrayList<>();
        LocalDateTime cursor = start;

        for (WorkSlot block : blocked) {
            // ① block 이 cursor 이전에 끝남 → 이미 지나친 block, skip
            if (!block.getEndTime().isAfter(cursor)) continue;
            // ② block 시작이 구간 밖 → 이후 block 도 모두 구간 밖, break
            if (!block.getStartTime().isBefore(end)) break;

            // ③ cursor ~ block.start 사이에 빈 구간 존재
            if (block.getStartTime().isAfter(cursor)) {
                available.add(new TimeInterval(cursor, block.getStartTime()));
            }
            // ④ cursor 를 block.end 로 전진 (end 초과 방지)
            cursor = block.getEndTime().isBefore(end) ? block.getEndTime() : end;
        }

        // 남은 구간
        if (cursor.isBefore(end)) {
            available.add(new TimeInterval(cursor, end));
        }

        return available;
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }

    // ── 배치 전용 ────────────────────────────────────────────────────────────────

    /**
     * 승인 기간의 날짜별 슬롯을 WorkSchedule 기준으로 분할하여 일괄 등록합니다.
     *
     * <p>LeaveService·FieldWorkService·BusinessTripService 의 approve() 에서
     * 공통으로 사용하는 슬롯 분할 등록 로직의 단일 진입점입니다.</p>
     *
     * <ul>
     *   <li>단일 날짜: startDateTime~endDateTime 을 schedule 범위로 clamp 하여 1개 슬롯 등록</li>
     *   <li>복수 날짜: 첫날 / 중간 전체일 / 마지막 날로 분리하여 등록</li>
     * </ul>
     *
     * @param memberId      대상 직원 ID
     * @param startDateTime 승인 구간 시작
     * @param endDateTime   승인 구간 종료
     * @param schedule      직원 근무 일정
     * @param workType      근무 유형 (OFFICE 사용 불가)
     */
    public void registerApprovedSlots(Long memberId,
                                      LocalDateTime startDateTime, LocalDateTime endDateTime,
                                      WorkSchedule schedule, WorkType workType) {
        LocalTime scheduleStart = schedule.getStartTime();
        LocalTime scheduleEnd = schedule.getEndTime();

        LocalDate startDate = startDateTime.toLocalDate();
        LocalDate endDate = endDateTime.toLocalDate();

        if (startDate.equals(endDate)) {
            registerApprovedSlot(
                    memberId, startDate,
                    LocalDateTime.of(startDate, max(startDateTime.toLocalTime(), scheduleStart)),
                    LocalDateTime.of(startDate, min(endDateTime.toLocalTime(), scheduleEnd)),
                    workType);
        } else {
            registerApprovedSlot(
                    memberId, startDate,
                    LocalDateTime.of(startDate, max(startDateTime.toLocalTime(), scheduleStart)),
                    LocalDateTime.of(startDate, scheduleEnd),
                    workType);

            LocalDate date = startDate.plusDays(1);
            while (date.isBefore(endDate)) {
                registerApprovedSlot(
                        memberId, date,
                        LocalDateTime.of(date, scheduleStart),
                        LocalDateTime.of(date, scheduleEnd),
                        workType);
                date = date.plusDays(1);
            }

            registerApprovedSlot(
                    memberId, endDate,
                    LocalDateTime.of(endDate, scheduleStart),
                    LocalDateTime.of(endDate, min(endDateTime.toLocalTime(), scheduleEnd)),
                    workType);
        }
    }

    /**
     * 승인 기반 근무 슬롯을 등록합니다.
     *
     * <ol>
     *   <li>해당 날짜의 WorkRecord 를 조회하거나 신규 생성</li>
     *   <li>기존 슬롯과 겹치는 구간이 있으면 {@link WorkRecordException} 발생</li>
     *   <li>WorkRecord 에 슬롯 추가 (dirty-check 으로 자동 저장)</li>
     * </ol>
     *
     * @param memberId  대상 직원 ID
     * @param bizDate   논리 근무일
     * @param startTime 슬롯 시작 시각
     * @param endTime   슬롯 종료 시각
     * @param workType  근무 유형 (OFFICE 사용 불가)
     * @throws WorkRecordException SLOT_CONFLICT — 이미 점유된 구간과 겹칠 때
     */
    public void registerApprovedSlot(Long memberId, LocalDate bizDate,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     WorkType workType) {
        if (workType == WorkType.OFFICE) {
            throw new WorkRecordException(WorkRecordError.CANNOT_DELETE_OFFICE_SLOT);
        }

        WorkRecord record = getOrCreate(memberId, bizDate);

        if (record.hasConflict(startTime, endTime)) {
            log.warn("[WorkRecord] 슬롯 충돌: memberId={}, date={}, {}~{}, type={}",
                    memberId, bizDate, startTime, endTime, workType);
            throw new WorkRecordException(WorkRecordError.SLOT_CONFLICT);
        }

        record.addSlot(startTime, endTime, workType);
        log.debug("[WorkRecord] 슬롯 등록: memberId={}, date={}, {}~{}, type={}",
                memberId, bizDate, startTime, endTime, workType);
    }

    // ── 차집합 기반 빈 슬롯 계산 (정적 유틸) ────────────────────────────────────

    /**
     * 승인 취소 — 특정 근무 유형의 슬롯을 모두 제거합니다.
     *
     * <p>OFFICE 슬롯은 배치 재처리를 통해서만 관리됩니다.</p>
     *
     * @throws WorkRecordException CANNOT_DELETE_OFFICE_SLOT — workType 이 OFFICE 일 때
     */
    public void removeApprovedSlots(Long memberId, LocalDate bizDate, WorkType workType) {
        if (workType == WorkType.OFFICE) {
            throw new WorkRecordException(WorkRecordError.CANNOT_DELETE_OFFICE_SLOT);
        }
        workRecordRepository.findByMemberIdAndBizDate(memberId, bizDate)
                .ifPresent(r -> {
                    r.removeSlotsByType(workType);
                    log.debug("[WorkRecord] 슬롯 제거: memberId={}, date={}, type={}", memberId, bizDate, workType);
                });
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    /**
     * 배치 멱등성 처리 — 특정 날짜의 OFFICE 슬롯만 초기화합니다.
     *
     * <p>해당 날짜의 WorkRecord 가 없으면 아무 동작도 하지 않습니다.
     * orphanRemoval 에 의해 실제 DELETE 는 트랜잭션 커밋 시 실행됩니다.</p>
     */
    public void clearOfficeSlots(Long memberId, LocalDate bizDate) {
        workRecordRepository.findByMemberIdAndBizDate(memberId, bizDate)
                .ifPresent(r -> r.removeSlotsByType(WorkType.OFFICE));
    }

    /**
     * WorkRecord 를 조회하거나 없으면 신규 생성·저장합니다.
     *
     * <p>배치 Processor 에서 chunk 트랜잭션 내에서 호출되므로
     * 기본 전파(REQUIRED) 를 사용합니다.</p>
     */
    public WorkRecord getOrCreate(Long memberId, LocalDate bizDate) {
        return workRecordRepository.findByMemberIdAndBizDate(memberId, bizDate)
                .orElseGet(() -> workRecordRepository.save(WorkRecord.of(memberId, bizDate)));
    }

    // ── Value Object ─────────────────────────────────────────────────────────────

    /**
     * 시간 구간 값 객체 (start 포함, end 미포함).
     */
    public record TimeInterval(LocalDateTime start, LocalDateTime end) {

        /**
         * 구간 지속 시간
         */
        public Duration duration() {
            return Duration.between(start, end);
        }

        /**
         * 지속 시간 (분)
         */
        public long durationMinutes() {
            return duration().toMinutes();
        }
    }
}
