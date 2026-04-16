package dev.batch.WorkRecord.listener;

import dev.workrecord.entity.WorkRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WorkRecord Write 단계 리스너.
 *
 * <p>Writer가 List&lt;WorkRecord&gt; 배치를 DB에 저장하는 시점에 호출됩니다.
 * 저장 실패 시 오류를 기록하여 retry/skip 후 추적 가능하도록 합니다.</p>
 */
@Slf4j
@Component
public class WorkRecordItemWriteListener implements ItemWriteListener<List<WorkRecord>> {

    @Override
    public void beforeWrite(Chunk<? extends List<WorkRecord>> items) {
        int totalSlots = items.getItems().stream().mapToInt(List::size).sum();
        log.debug("[WorkRecord][WRITE] {}명 분 WorkRecord {}건 저장 시작",
                items.size(), totalSlots);
    }

    @Override
    public void afterWrite(Chunk<? extends List<WorkRecord>> items) {
        int totalSlots = items.getItems().stream().mapToInt(List::size).sum();
        log.debug("[WorkRecord][WRITE] WorkRecord {}건 저장 완료", totalSlots);
    }

    @Override
    public void onWriteError(Exception ex, Chunk<? extends List<WorkRecord>> items) {
        log.error("[WorkRecord][WRITE] 저장 오류 | 대상={}명 | cause={}: {}",
                items.size(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
