package dev.test;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DcItemRepository extends JpaRepository<DirtyCheckCostTest.DcItem, Long> {

    // JPQL 변환 → 실행 직전 onAutoFlush 트리거 (findById는 PK 조회라 트리거되지 않음)
    Optional<DirtyCheckCostTest.DcItem> findByValue(Long value);
}
