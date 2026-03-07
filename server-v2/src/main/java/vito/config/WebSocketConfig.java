package vito.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import vito.handler.ChatWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final RoomIdHandshakeInterceptor roomIdHandshakeInterceptor;

    /**
     * @param chatWebSocketHandler        handler for chat WebSocket messages
     * @param roomIdHandshakeInterceptor extracts and validates room id from path
     */
    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
            RoomIdHandshakeInterceptor roomIdHandshakeInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.roomIdHandshakeInterceptor = roomIdHandshakeInterceptor;
    }

    /** @param registry WebSocket handler registry to register the handler */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/chat/*")
                .addInterceptors(roomIdHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
