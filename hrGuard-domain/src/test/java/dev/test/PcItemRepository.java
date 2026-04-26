package dev.test;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PcItemRepository extends JpaRepository<PersistenceContextTest.PcItem, Long> {
}
