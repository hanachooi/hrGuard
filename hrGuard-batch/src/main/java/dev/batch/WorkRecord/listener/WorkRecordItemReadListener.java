package dev.batch.WorkRecord.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.stereotype.Component;

/**
 * WorkRecordSync Read 단계 리스너.
 *
 * <p>Reader가 memberId를 읽는 시점에 호출됩니다.
 * 읽기 오류 발생 시 원인을 기록하고 fault-tolerant skip 판단 근거를 남깁니다.</p>
 */
@Slf4j
@Component
public class WorkRecordItemReadListener implements ItemReadListener<Long> {

    @Override
    public void beforeRead() {
        // 고빈도 호출 — 기본적으로 로그 생략, 필요 시 TRACE 레벨 활성화
        log.trace("[WorkRecordSync][READ] beforeRead");
    }

    @Override
    public void afterRead(Long memberId) {
        log.debug("[WorkRecordSync][READ] memberId={} 읽기 완료", memberId);
    }

    @Override
    public void onReadError(Exception ex) {
        log.error("[WorkRecordSync][READ] 읽기 오류 | cause={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
