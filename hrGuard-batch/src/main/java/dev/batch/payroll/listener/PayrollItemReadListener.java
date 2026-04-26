package dev.batch.payroll.listener;

import dev.batch.payroll.dto.PayrollInputDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PayrollItemReadListener implements ItemReadListener<PayrollInputDto> {

    @Override
    public void beforeRead() {
        log.trace("[Payroll][READ] beforeRead");
    }

    @Override
    public void afterRead(PayrollInputDto input) {
        log.debug("[Payroll][READ] memberId={} 읽기 완료", input.memberId());
    }

    @Override
    public void onReadError(Exception ex) {
        log.error("[Payroll][READ] 읽기 오류 | cause={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
