package dev.batch.WorkRecord.listener;

import dev.workrecord.entity.WorkRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WorkRecordSync Process 단계 리스너.
 *
 * <p>Processor(CommuteSyncProcessor)가 memberId → List&lt;WorkRecord&gt;를 변환하는
 * 시점에 호출됩니다. null 반환(슬롯 없음)은 Spring Batch가 filterCount로 처리하며
 * 이 리스너에서도 DEBUG 로그를 남깁니다.</p>
 */
@Slf4j
@Component
public class WorkRecordItemProcessListener implements ItemProcessListener<Long, List<WorkRecord>> {

    @Override
    public void beforeProcess(Long memberId) {
        log.debug("[WorkRecordSync][PROCESS] memberId={} 처리 시작", memberId);
    }

    @Override
    public void afterProcess(Long memberId, List<WorkRecord> result) {
        if (result == null) {
            log.debug("[WorkRecordSync][PROCESS] memberId={} → 슬롯 없음 (skip)", memberId);
        } else {
            log.debug("[WorkRecordSync][PROCESS] memberId={} → WorkRecord {}건 생성", memberId, result.size());
        }
    }

    @Override
    public void onProcessError(Long memberId, Exception ex) {
        log.error("[WorkRecordSync][PROCESS] memberId={} 처리 오류 | cause={}: {}",
                memberId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
