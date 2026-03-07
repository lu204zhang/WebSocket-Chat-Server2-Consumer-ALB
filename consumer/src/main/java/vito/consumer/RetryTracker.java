package vito.consumer;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetryTracker {

    private final int maxRetries;
    private final int retryTrackMaxSize;
    private final ConcurrentHashMap<String, Integer> messageIdToRetryCount = new ConcurrentHashMap<>();

    /**
     * @param maxRetries        max retries before giving up
     * @param retryTrackMaxSize max entries to track
     */
    public RetryTracker(
            @Value("${app.consumer.max-retries:3}") int maxRetries,
            @Value("${app.consumer.retry-track-max-size:50000}") int retryTrackMaxSize) {
        this.maxRetries = maxRetries;
        this.retryTrackMaxSize = retryTrackMaxSize;
    }

    /** @param messageId message id @return current retry count (0 if never retried) */
    public int getRetryCount(String messageId) {
        return messageIdToRetryCount.getOrDefault(messageId, 0);
    }

    /** @param messageId message id @return true if messageId has reached max retries */
    public boolean exceedsMaxRetries(String messageId) {
        return getRetryCount(messageId) >= maxRetries;
    }

    /** @param messageId message id @return the new retry count after increment */
    public int incrementAndGet(String messageId) {
        evictIfNeeded();
        return messageIdToRetryCount.compute(messageId, (k, v) -> v == null ? 1 : v + 1);
    }

    /** @param messageId message id to remove from retry tracking */
    public void remove(String messageId) {
        messageIdToRetryCount.remove(messageId);
    }

    /** @return configured max retries per message */
    public int getMaxRetries() {
        return maxRetries;
    }

    private void evictIfNeeded() {
        if (messageIdToRetryCount.size() <= retryTrackMaxSize) {
            return;
        }
        messageIdToRetryCount.entrySet().removeIf(e -> e.getValue() >= maxRetries);
    }
}
