package dev.batch.payroll.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.stereotype.Component;

/**
 * Payroll Read 단계 리스너.
 *
 * <p>Reader가 급여 계산 대상 memberId를 읽는 시점에 호출됩니다.
 * 읽기 오류 발생 시 원인을 기록합니다.</p>
 */
@Slf4j
@Component
public class PayrollItemReadListener implements ItemReadListener<Long> {

    @Override
    public void beforeRead() {
        log.trace("[Payroll][READ] beforeRead");
    }

    @Override
    public void afterRead(Long memberId) {
        log.debug("[Payroll][READ] memberId={} 읽기 완료", memberId);
    }

    @Override
    public void onReadError(Exception ex) {
        log.error("[Payroll][READ] 읽기 오류 | cause={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
