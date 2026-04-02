package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.DocumentState;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncType;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DocumentWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DocumentWebSocketHandler.class);

    private final Map<String, Set<WebSocketSession>> docSessions = new ConcurrentHashMap<>();
    private final jakarta.validation.Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    private final DocumentOperationService documentOperationService;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String docId = getDocId(session);

        docSessions.computeIfAbsent(docId, x -> ConcurrentHashMap.newKeySet())
                .add(session);

        DocumentState doc = documentOperationService.initDocument(docId);

        String payload = mapper.writeValueAsString(Map.of(
                "type", "init",
                "content", doc.getContent(),
                "version", doc.getVersion()
        ));
        session.sendMessage(new TextMessage(payload));

        log.info("Connected. ID: {} | Doc: {}", session.getId(), docId);
    }

    @Override
    public void handleMessage(
            @NonNull WebSocketSession session,
            @NonNull WebSocketMessage<?> message
    ) throws Exception {
        String docId = getDocId(session);

        try {
            Operation op = mapper.readValue(message.getPayload().toString(), Operation.class);

            var violations = validator.validate(op);
            if (!violations.isEmpty()) {
                String errorMsg = violations.iterator().next().getMessage();
                log.error("Validation failed for session: {}. Error: {}", session.getId(), errorMsg);
                session.sendMessage(new TextMessage("{\"error\": \"" + errorMsg + "\"}"));
                return;
            }

            log.info("Received op: {}", op);

            DocumentHandleOperationResponse response = documentOperationService.handle(docId, op);
            if (response.getSyncMessage() != null) {
                TextMessage textMessage = new TextMessage(mapper.writeValueAsString(response.getSyncMessage()));
                session.sendMessage(textMessage);
                if (response.getSyncMessage().getType().equals(SyncType.FULL)) return;
            }

            String msg = mapper.writeValueAsString(response.getOp());

            broadcast(docId, session, msg);
        } catch (Exception e) {
            log.error("Failed to initialize message:", e);
            session.sendMessage(new TextMessage("{\"error\": \"Invalid JSON format\"}"));
        }
    }

    private void broadcast(String docId, WebSocketSession sender, String msg) throws IOException {
        for (WebSocketSession s : docSessions.getOrDefault(docId, Set.of())) {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                s.sendMessage(new TextMessage(msg));
            }
        }
    }

    @Override
    public void afterConnectionClosed(
            @NonNull WebSocketSession session,
            @NonNull CloseStatus closeStatus
    ) {
        String docId = getDocId(session);
        Set<WebSocketSession> sessions = docSessions.get(docId);

        if (!sessions.isEmpty()) {
            sessions.remove(session);
        }

        log.info("Disconnected. ID: {} | Doc: {}", session.getId(), docId);
    }

    private String getDocId(WebSocketSession session) {
        return UriComponentsBuilder.fromUri(Objects.requireNonNull(session.getUri()))
                .build()
                .getQueryParams()
                .getFirst("docId");
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {}

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
