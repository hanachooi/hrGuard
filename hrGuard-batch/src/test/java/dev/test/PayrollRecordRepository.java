package dev.test;

import org.springframework.data.jpa.repository.JpaRepository;

interface PayrollRecordRepository extends JpaRepository<BatchWriterTest.PayrollRecord, Long> {
}
