package vito.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vito.room.RoomSessionManager;

import static vito.config.Constants.ROOM_ID_ATTR;

@Component
public class BroadcastWebSocketHandler extends TextWebSocketHandler {
    private final RoomSessionManager roomSessionManager;

    /** @param roomSessionManager used to add/remove sessions by room */
    public BroadcastWebSocketHandler(RoomSessionManager roomSessionManager) {
        this.roomSessionManager = roomSessionManager;
    }

    /** @param session new WebSocket session; registers it in the room from handshake attributes */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = (String) session.getAttributes().get(ROOM_ID_ATTR);
        if (roomId != null) {
            roomSessionManager.addSession(roomId, session);
        }
    }

    /**
     * @param session WebSocket session that closed
     * @param status  close status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get(ROOM_ID_ATTR);
        if (roomId != null) {
            roomSessionManager.removeSession(roomId, session);
        }
    }
}
