package dev.batch.payroll.listener;

import dev.payroll.entity.MonthlyPayroll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.stereotype.Component;

/**
 * Payroll Process 단계 리스너.
 *
 * <p>Processor가 memberId → MonthlyPayroll을 생성하는 시점에 호출됩니다.
 * null 반환(WorkSchedule 없음 skip)과 정상 생성을 구분하여 기록합니다.</p>
 */
@Slf4j
@Component
public class PayrollItemProcessListener implements ItemProcessListener<Long, MonthlyPayroll> {

    @Override
    public void beforeProcess(Long memberId) {
        log.debug("[Payroll][PROCESS] memberId={} 급여 계산 시작", memberId);
    }

    @Override
    public void afterProcess(Long memberId, MonthlyPayroll result) {
        if (result == null) {
            log.debug("[Payroll][PROCESS] memberId={} → WorkSchedule 없음 (skip)", memberId);
        } else {
            log.debug("[Payroll][PROCESS] memberId={} → 총액={}원 계산 완료",
                    memberId, result.getTotalAmount());
        }
    }

    @Override
    public void onProcessError(Long memberId, Exception ex) {
        log.error("[Payroll][PROCESS] memberId={} 급여 계산 오류 | cause={}: {}",
                memberId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
