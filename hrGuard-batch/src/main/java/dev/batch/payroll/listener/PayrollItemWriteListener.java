package dev.batch.payroll.listener;

import dev.payroll.entity.MonthlyPayroll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;

/**
 * Payroll Write 단계 리스너.
 *
 * <p>Writer가 MonthlyPayroll(+ PayrollItem)을 DB에 저장하는 시점에 호출됩니다.
 * 저장 실패 시 오류를 기록하여 retry/skip 후 추적 가능하도록 합니다.</p>
 */
@Slf4j
@Component
public class PayrollItemWriteListener implements ItemWriteListener<MonthlyPayroll> {

    @Override
    public void beforeWrite(Chunk<? extends MonthlyPayroll> items) {
        log.debug("[Payroll][WRITE] {}명 분 MonthlyPayroll 저장 시작", items.size());
    }

    @Override
    public void afterWrite(Chunk<? extends MonthlyPayroll> items) {
        long totalAmount = items.getItems().stream()
                .mapToLong(MonthlyPayroll::getTotalAmount)
                .sum();
        log.debug("[Payroll][WRITE] {}명 저장 완료 | 청크 합계={}원", items.size(), totalAmount);
    }

    @Override
    public void onWriteError(Exception ex, Chunk<? extends MonthlyPayroll> items) {
        log.error("[Payroll][WRITE] 저장 오류 | 대상={}명 | cause={}: {}",
                items.size(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
