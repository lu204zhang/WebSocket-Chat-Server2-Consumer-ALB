package vito.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import vito.model.QueueMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static vito.config.Constants.exchangeName;
import static vito.config.Constants.ROOM_QUEUE_PREFIX;

public class QueuePublisher {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final ChannelPool channelPool;
    private final ObjectMapper objectMapper;
    private final int failureThreshold;
    private final long openDurationMillis;
    private final Object lock = new Object();

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long openAtMillis = 0L;

    /**
     * @param channelPool         channel pool for publishing
     * @param objectMapper        for JSON serialization
     * @param failureThreshold    number of failures before opening the circuit
     * @param openDurationSeconds how long the circuit stays open before half-open
     */
    public QueuePublisher(ChannelPool channelPool, ObjectMapper objectMapper,
            int failureThreshold, long openDurationSeconds) {
        this.channelPool = channelPool;
        this.objectMapper = objectMapper;
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationSeconds * 1000L;
    }

    /**
     * Publishes the message to the queue for the message's room.
     * @param message message to publish; must have valid roomId
     * @throws CircuitOpenException if circuit breaker is open
     * @throws Exception            if serialization or publish fails
     */
    public void publish(QueueMessage message) throws Exception {
        throwIfCircuitOpen();
        Channel channel = null;
        try {
            String roomId = message.getRoomId();
            String routingKey = ROOM_QUEUE_PREFIX + roomId;
            String jsonMessage = objectMapper.writeValueAsString(message);
            byte[] bodyBytes = jsonMessage.getBytes(StandardCharsets.UTF_8);
            channel = channelPool.borrowChannel();
            channel.basicPublish(exchangeName, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, bodyBytes);
            recordSuccess();
        } catch (CircuitOpenException e) {
            throw e;
        } catch (Exception e) {
            recordFailure();
            throw e;
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }

    private void throwIfCircuitOpen() {
        synchronized (lock) {
            if (state == State.CLOSED) {
                return;
            }
            if (state == State.OPEN) {
                long elapsed = System.currentTimeMillis() - openAtMillis;
                if (elapsed >= openDurationMillis) {
                    state = State.HALF_OPEN;
                    return;
                }
                throw new CircuitOpenException();
            }
            return;
        }
    }

    private void recordSuccess() {
        synchronized (lock) {
            failureCount.set(0);
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
            }
        }
    }

    private void recordFailure() {
        synchronized (lock) {
            int count = failureCount.incrementAndGet();
            if (state == State.HALF_OPEN || count >= failureThreshold) {
                state = State.OPEN;
                openAtMillis = System.currentTimeMillis();
            }
        }
    }
}
