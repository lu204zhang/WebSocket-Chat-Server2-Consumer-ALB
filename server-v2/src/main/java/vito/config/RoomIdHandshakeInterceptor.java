package vito.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class RoomIdHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ROOM_ID_ATTR = "roomId";

    /**
     * Parses room id from path, validates range, puts it in attributes.
     * @param request    HTTP request
     * @param response   HTTP response (status set to 400 on failure)
     * @param wsHandler  WebSocket handler
     * @param attributes handshake attributes to store roomId
     * @return true if path has valid room id and handshake may continue; false otherwise
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        String roomIdStr = path.substring(lastSlash + 1);
        if (roomIdStr.isEmpty()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        int roomId;
        try {
            roomId = Integer.parseInt(roomIdStr);
        } catch (NumberFormatException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        if (roomId < Constants.MIN_ROOM_ID || roomId > Constants.MAX_ROOM_ID) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        attributes.put(ROOM_ID_ATTR, String.valueOf(roomId));
        return true;
    }

    /**
     * No-op. Called after handshake completes.
     * @param request   HTTP request
     * @param response  HTTP response
     * @param wsHandler WebSocket handler
     * @param exception exception from handshake, or null
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }
}
