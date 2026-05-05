package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import astranewin.dev.realtime_collaborative_editor.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        URI uri = request.getURI();

        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        String token = params.getFirst("wsToken");
        String docId = params.getFirst("docId");

        if (token == null || docId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            String effectiveAccess = jwtService.extractEffective(token);
            String explicitAccess = jwtService.extractExplicitAccess(token);
            UserDetails userDetails = jwtService.authWebSocketToken(token);

            attributes.put("wsToken", token);
            attributes.put("docId", docId);
            attributes.put("username", userDetails.getUsername());
            attributes.put("effectiveAccess", AccessType.valueOf(effectiveAccess));
            attributes.put("explicitAccess", AccessType.valueOf(explicitAccess));
            return true;
        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {

    }
}
