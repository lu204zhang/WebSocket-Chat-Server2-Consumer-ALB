package vito.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vito.queue.ChannelPool;
import vito.queue.QueuePublisher;

@Configuration
public class RabbitMQConfig {

    /**
     * Creates channel pool, declares exchange and per-room queues. Destroy method closes the pool.
     * @param connectionFactory RabbitMQ connection factory
     * @param poolSize          number of channels in the pool
     * @param messageTtlMs      message TTL in ms for queue declaration
     * @param queueMaxLength    max messages per queue
     * @return initialized channel pool
     */
    @Bean(destroyMethod = "close")
    public ChannelPool channelPool(ConnectionFactory connectionFactory,
            @Value("${app.rabbitmq.pool-size:10}") int poolSize,
            @Value("${app.rabbitmq.message-ttl-ms:60000}") int messageTtlMs,
            @Value("${app.rabbitmq.queue-max-length:1000}") int queueMaxLength) {
        ChannelPool channelPool = new ChannelPool(connectionFactory, poolSize, messageTtlMs, queueMaxLength);
        channelPool.init();
        return channelPool;
    }

    /**
     * Creates queue publisher with circuit breaker.
     * @param channelPool         channel pool for publishing
     * @param objectMapper        for JSON serialization
     * @param failureThreshold    number of failures before opening the circuit
     * @param openDurationSeconds how long the circuit stays open before half-open
     * @return queue publisher
     */
    @Bean
    public QueuePublisher queuePublisher(ChannelPool channelPool, ObjectMapper objectMapper,
            @Value("${app.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${app.circuit-breaker.open-duration-seconds:30}") long openDurationSeconds) {
        return new QueuePublisher(channelPool, objectMapper, failureThreshold, openDurationSeconds);
    }
}
