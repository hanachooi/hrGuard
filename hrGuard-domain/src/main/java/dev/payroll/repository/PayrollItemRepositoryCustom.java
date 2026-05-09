package dev.payroll.repository;

import dev.payroll.repository.projection.PayrollItemProjection;

import java.util.List;

public interface PayrollItemRepositoryCustom {

    void batchInsert(List<PayrollItemProjection> items);
}
