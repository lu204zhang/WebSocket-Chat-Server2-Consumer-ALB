package vito.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class ConsumerMetrics {

    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    private final AtomicLong messagesSkippedDuplicate = new AtomicLong(0);

    /** Increments processed message count. */
    public void recordProcessed() {
        messagesProcessed.incrementAndGet();
    }

    /** Increments failed message count. */
    public void recordFailed() {
        messagesFailed.incrementAndGet();
    }

    /** Increments skipped-duplicate count. */
    public void recordSkippedDuplicate() {
        messagesSkippedDuplicate.incrementAndGet();
    }

    /** @return total messages processed */
    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }

    /** @return total messages failed */
    public long getMessagesFailed() {
        return messagesFailed.get();
    }

    /** @return total messages skipped as duplicate */
    public long getMessagesSkippedDuplicate() {
        return messagesSkippedDuplicate.get();
    }
}
