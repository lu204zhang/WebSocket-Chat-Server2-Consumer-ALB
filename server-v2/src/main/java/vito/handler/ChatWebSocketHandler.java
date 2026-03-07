package vito.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vito.config.RoomIdHandshakeInterceptor;
import vito.model.ChatMessage;
import vito.model.MessageType;
import vito.model.QueueMessage;
import vito.model.ServerResponse;
import vito.validator.MessageValidator;
import vito.validator.ValidationResult;
import vito.queue.CircuitOpenException;
import vito.queue.QueuePublisher;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final String JOINED_KEY = "joined";
    private static final String USER_ID_KEY = "userId";
    private final MessageValidator messageValidator;
    private final ObjectMapper objectMapper;
    private final QueuePublisher queuePublisher;
    private final String serverId;

    /**
     * @param messageValidator validator for incoming chat messages
     * @param objectMapper     for JSON serialization
     * @param queuePublisher   publisher for queue
     * @param serverId         server identifier (optional; resolved from hostname if blank)
     */
    public ChatWebSocketHandler(MessageValidator messageValidator, ObjectMapper objectMapper,
            QueuePublisher queuePublisher,
            @Value("${app.server.id:}") String serverId) {
        this.messageValidator = messageValidator;
        this.objectMapper = objectMapper;
        this.queuePublisher = queuePublisher;
        this.serverId = serverId.isBlank() ? resolveHostname() : serverId;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Marks the session as not yet joined.
     * @param session new WebSocket session
     * @throws Exception if an error occurs
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.getAttributes().put(JOINED_KEY, false);
    }

    /**
     * Parses and validates payload, publishes to room queue, sends OK or ERROR response.
     * @param session WebSocket session (must have roomId in attributes)
     * @param message text message containing JSON ChatMessage payload
     * @throws Exception if an error occurs
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        ChatMessage chatMessage = null;
        try {
            chatMessage = objectMapper.readValue(payload, ChatMessage.class);
        } catch (JsonProcessingException e) {
            ServerResponse response = new ServerResponse("ERROR", Instant.now(), "Unable to parse JSON");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        ValidationResult result = messageValidator.validate(chatMessage);
        ServerResponse response = null;
        if (!result.isValid()) {
            response = new ServerResponse("ERROR", Instant.now(), result.getMessage());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        String roomId = (String) session.getAttributes().get(RoomIdHandshakeInterceptor.ROOM_ID_ATTR);
        if (roomId == null) {
            response = new ServerResponse("ERROR", Instant.now(), "Missing or invalid room");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        QueueMessage queueMessage = buildQueueMessage(roomId, chatMessage, session);
        try {
            queuePublisher.publish(queueMessage);
        } catch (CircuitOpenException e) {
            response = new ServerResponse("ERROR", Instant.now(),
                    "Message queuing temporarily unavailable");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        } catch (Exception e) {
            response = new ServerResponse("ERROR", Instant.now(),
                    "Message queuing temporarily unavailable");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        Map<String, Object> attributes = session.getAttributes();
        MessageType messageType = chatMessage.getMessageType();
        switch (messageType) {
            case JOIN:
                attributes.put(JOINED_KEY, true);
                attributes.put(USER_ID_KEY, chatMessage.getUserId());
                response = new ServerResponse("OK", Instant.now(),
                        "You have joined the chat: " + chatMessage.getMessage());
                break;
            case LEAVE:
                response = new ServerResponse("OK", Instant.now(),
                        "You have left the chat: " + chatMessage.getMessage());
                break;
            case TEXT:
                response = new ServerResponse("OK", Instant.now(), chatMessage.getMessage());
                break;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private QueueMessage buildQueueMessage(String roomId, ChatMessage chatMessage, WebSocketSession session) {
        QueueMessage q = new QueueMessage();
        q.setMessageId(UUID.randomUUID().toString());
        q.setRoomId(roomId);
        q.setUserId(chatMessage.getUserId());
        q.setUsername(chatMessage.getUserName());
        q.setMessage(chatMessage.getMessage());
        q.setTimestamp(chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : Instant.now());
        q.setMessageType(chatMessage.getMessageType());
        q.setServerId(serverId);
        q.setClientIp(session.getRemoteAddress() != null && session.getRemoteAddress().getAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress()
                : "");
        return q;
    }

    /**
     * Clears session attributes after connection closed.
     * @param session     closed session
     * @param closeStatus  close status
     * @throws Exception if an error occurs
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        session.getAttributes().clear();
    }
}
