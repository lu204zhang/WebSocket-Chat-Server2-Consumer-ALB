package vito.metrics;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    private final ConsumerMetrics consumerMetrics;

    /** @param consumerMetrics source of processed/failed/skipped counts */
    public MetricsController(ConsumerMetrics consumerMetrics) {
        this.consumerMetrics = consumerMetrics;
    }

    /** @return map with messagesProcessed, messagesFailed, messagesSkippedDuplicate */
    @GetMapping("/app/metrics")
    public Map<String, Long> metrics() {
        return Map.of(
                "messagesProcessed", consumerMetrics.getMessagesProcessed(),
                "messagesFailed", consumerMetrics.getMessagesFailed(),
                "messagesSkippedDuplicate", consumerMetrics.getMessagesSkippedDuplicate()
        );
    }
}
