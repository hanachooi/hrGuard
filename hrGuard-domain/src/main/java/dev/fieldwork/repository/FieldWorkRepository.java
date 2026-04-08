package dev.fieldwork.repository;

import dev.fieldwork.constant.FieldWorkStatus;
import dev.fieldwork.entity.FieldWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldWorkRepository extends JpaRepository<FieldWork, Long> {

    List<FieldWork> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<FieldWork> findByStatusOrderByCreatedAtDesc(FieldWorkStatus status);
}
