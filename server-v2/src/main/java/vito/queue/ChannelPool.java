package vito.queue;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static vito.config.Constants.exchangeName;
import static vito.config.Constants.MAX_ROOM_ID;
import static vito.config.Constants.MIN_ROOM_ID;
import static vito.config.Constants.ROOM_QUEUE_PREFIX;

public class ChannelPool {
    private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);
    private final ConnectionFactory connectionFactory;
    private Connection connection;
    private final BlockingQueue<Channel> pool;
    private final int poolSize;
    private final int messageTtlMs;
    private final int queueMaxLength;

    /**
     * @param connectionFactory RabbitMQ connection factory
     * @param poolSize          number of channels in the pool
     * @param messageTtlMs      message TTL for queue declaration
     * @param queueMaxLength    max messages per queue
     */
    public ChannelPool(ConnectionFactory connectionFactory, int poolSize, int messageTtlMs, int queueMaxLength) {
        this.connectionFactory = connectionFactory;
        this.poolSize = poolSize;
        this.messageTtlMs = messageTtlMs;
        this.queueMaxLength = queueMaxLength;
        pool = new ArrayBlockingQueue<>(poolSize);
    }

    /**
     * Declares exchange and per-room queues, pre-creates channels.
     * @throws RuntimeException if exchange or queue declaration fails
     */
    public void init() {
        this.connection = connectionFactory.createConnection();
        for (int i = 0; i < poolSize; i++) {
            Channel channel = connection.createChannel(false);
            if (channel == null) {
                throw new IllegalStateException("ConnectionFactory.createChannel returned null");
            }
            if (i == 0) {
                try {
                    channel.exchangeDeclare(exchangeName, "topic", true);

                    Map<String, Object> args = new HashMap<>();
                    args.put("x-message-ttl", messageTtlMs);
                    args.put("x-max-length", queueMaxLength);

                    for (int roomId = MIN_ROOM_ID; roomId <= MAX_ROOM_ID; roomId++) {
                        String queueName = ROOM_QUEUE_PREFIX + roomId;
                        String routingKey = queueName;
                        channel.queueDeclare(queueName, true, false, false, args);
                        channel.queueBind(queueName, exchangeName, routingKey);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to declare exchange/queues", e);
                }
            }
            pool.add(channel);
        }
    }

    /**
     * Takes a channel from the pool; blocks until available. Replaces closed channels.
     * @return open channel, never null
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if a new channel cannot be created
     */
    public Channel borrowChannel() throws InterruptedException {
        Channel channel = pool.take();
        if (channel.isOpen()) {
            return channel;
        }
        try {
            channel.close();
        } catch (Exception e) {
            log.debug("Channel close failed (likely already closed)", e);
        }
        channel = connection.createChannel(false);
        if (channel == null) {
            throw new IllegalStateException("ConnectionFactory.createChannel returned null");
        }
        return channel;
    }

    /**
     * Returns the channel to the pool; replaces it if closed.
     * @param channel channel to return (null is ignored)
     */
    public void returnChannel(Channel channel) {
        if (channel == null) {
            return;
        }
        if (channel.isOpen()) {
            pool.offer(channel);
        } else {
            try {
                channel.close();
            } catch (Exception e) {
                log.debug("Channel close failed (likely already closed)", e);
            }
            Channel newChannel = connection.createChannel(false);
            if (newChannel != null) {
                pool.offer(newChannel);
            }
        }
    }

    /** Closes all pooled channels and the connection. Safe to call multiple times. */
    public void close() {
        Channel channel;
        while ((channel = pool.poll()) != null) {
            try {
                channel.close();
            } catch (Exception e) {
                log.warn("Failed to close channel during pool shutdown", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.warn("Failed to close connection during pool shutdown", e);
            }
        }
    }
}
