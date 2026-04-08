package dev.commute.entity;

import dev.common.BaseEntity;
import dev.commute.constant.CommuteStatus;
import dev.commute.constant.CommuteType;
import dev.commute.exception.CommuteError;
import dev.commute.exception.CommuteException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        // 하루에 여러 번 출퇴근 가능 (외근 후 복귀 등): unique constraint 없음
        // 대신 애플리케이션 레벨에서 status = CHECKIN 세션 존재 시 checkIn 차단
        indexes = {
                @Index(name = "idx_commute_work_date", columnList = "work_date"),
                @Index(name = "idx_commute_member_work_date", columnList = "member_id, work_date")
        }
)
public class Commute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK 없이 memberId만 저장 → DB 계층 부하 제거 (토큰으로 이미 검증된 값)
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 논리 근무일 (야간 근무 등 고려한 비즈니스 날짜)
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommuteStatus status;

    // 출근 시 null → 비동기로 WORK / HOLIDAY / DAYOFF 결정 후 채워짐
    @Enumerated(EnumType.STRING)
    @Column(name = "commute_type")
    private CommuteType commuteType;

    @Column(name = "in_time", nullable = false)
    private LocalDateTime inTime;

    @Column(name = "out_time")
    private LocalDateTime outTime;

    @Builder
    private Commute(Long memberId, LocalDate workDate, CommuteStatus status, LocalDateTime inTime) {
        this.memberId = memberId;
        this.workDate = workDate;
        this.status = status;
        this.inTime = inTime;
    }

    public static Commute checkIn(Long memberId) {
        return Commute.builder()
                .memberId(memberId)
                .workDate(LocalDate.now())
                .status(CommuteStatus.CHECKIN)
                .inTime(LocalDateTime.now())
                .build();
    }

    public void checkOut() {
        if (this.status == CommuteStatus.CHECKOUT) {
            throw new CommuteException(CommuteError.CHECKOUT_ALREADY);
        }
        this.outTime = LocalDateTime.now();
        this.status = CommuteStatus.CHECKOUT;
    }

    public void updateCommuteType(CommuteType commuteType) {
        this.commuteType = commuteType;
    }
}
