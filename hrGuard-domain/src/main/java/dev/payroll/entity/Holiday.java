package dev.payroll.entity;

import dev.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// 공휴일 테이블 (공공데이터포털 API로 연간 배치 적재)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"date"}))
public class Holiday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String name;
}
