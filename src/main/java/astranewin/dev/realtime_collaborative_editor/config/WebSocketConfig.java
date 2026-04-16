package astranewin.dev.realtime_collaborative_editor.config;

import astranewin.dev.realtime_collaborative_editor.document.edit.AuthHandshakeInterceptor;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final DocumentWebSocketHandler handler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(handler, "/ws/doc")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
