package dev.batch.common.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Slack Incoming Webhook 발송.
 *
 * <p>알람 실패가 배치 결과에 영향을 주지 않도록 모든 예외를 내부에서 catch 한다.</p>
 */
@Slf4j
@Component
public class SlackNotifier {

    private final String webhookUrl;
    private final RestTemplate restTemplate;

    public SlackNotifier(@Value("${slack.webhook-url:}") String webhookUrl,
                         RestTemplate restTemplate) {
        this.webhookUrl = webhookUrl;
        this.restTemplate = restTemplate;
    }

    public void sendBatchStop(String yearMonth, String status, long elapsedMs, String causeMessage) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("SLACK_WEBHOOK_URL 미설정 — Slack 알람 생략");
            return;
        }
        try {
            String body = buildPayload(yearMonth, status, elapsedMs, causeMessage);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(webhookUrl, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.error("Slack 알람 발송 실패 (배치 결과에 영향 없음) | cause={}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String buildPayload(String yearMonth, String status, long elapsedMs, String causeMessage) {
        long seconds = elapsedMs / 1000;
        String duration = String.format("%dm %ds", seconds / 60, seconds % 60);
        String escaped = causeMessage == null ? "-" : causeMessage.replace("\"", "'").replace("\n", " ");

        return """
                {
                  "attachments": [{
                    "color": "#FF0000",
                    "title": "[Batch] STOP — 급여 정산 비정상 종료",
                    "fields": [
                      {"title": "대상 월",    "value": "%s", "short": true},
                      {"title": "상태",       "value": "%s", "short": true},
                      {"title": "소요 시간",  "value": "%s", "short": true},
                      {"title": "원인",       "value": "%s", "short": false}
                    ],
                    "footer": "hrGuard Batch"
                  }]
                }
                """.formatted(yearMonth, status, duration, escaped);
    }
}
