package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.DocumentAccessPolicy;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.DocumentState;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncType;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DocumentWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DocumentWebSocketHandler.class);

    // That tracking works only with single-server setup.
    // 'Load balancing' might store data in different servers. Perhaps, Redis Pub/Sub might help
    // For the sake of not overwhelming the system, I decided to skip Redis part for now.

    // Document tracking/broadcast. DocId -> Session
    private final Map<String, Set<WebSocketSession>> docSessions = new ConcurrentHashMap<>();
    // Management map to work with a specific sessions by their username. Username -> Sessions
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    private final jakarta.validation.Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentOperationService documentOperationService;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String docId = (String) session.getAttributes().get("docId");
        String username = (String) session.getAttributes().get("username");

        if (username == null || docId == null) {
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        docSessions.computeIfAbsent(docId, x -> ConcurrentHashMap.newKeySet()).add(session);
        userSessions.computeIfAbsent(username, x -> ConcurrentHashMap.newKeySet()).add(session);

        DocumentState doc = documentOperationService.initDocument(docId);

        String payload = mapper.writeValueAsString(Map.of(
                "type", "init",
                "content", doc.getContent(),
                "version", doc.getVersion()
        ));
        sendMessageSafe(session, new TextMessage(payload));
        log.info("Connected. ID: {} | Doc: {}", session.getId(), docId);
    }

    @Override
    public void handleMessage(
            @NonNull WebSocketSession session,
            @NonNull WebSocketMessage<?> message
    ) throws Exception {
        AccessType effectiveAccess = (AccessType) session.getAttributes().get("effectiveAccess");
        String docId = (String) session.getAttributes().get("docId");

        if (effectiveAccess == AccessType.NONE || effectiveAccess == AccessType.READ) {
            sendMessageSafe(session, new TextMessage("{\"error\": \"No access to edit content\"}"));
            return;
        }

        try {
            Operation op = mapper.readValue(message.getPayload().toString(), Operation.class);

            var violations = validator.validate(op);
            if (!violations.isEmpty()) {
                String errorMsg = violations.iterator().next().getMessage();
                sendMessageSafe(session, new TextMessage("{\"error\": \"" + errorMsg + "\"}"));
                return;
            }

            DocumentHandleOperationResponse response = documentOperationService.handle(docId, op);

            if (response.getSyncMessage() != null) {
                sendMessageSafe(session, new TextMessage(mapper.writeValueAsString(response.getSyncMessage())));
                if (response.getSyncMessage().getType().equals(SyncType.FULL)) return;
            }

            broadcast(docId, session, mapper.writeValueAsString(response.getOp()));
        } catch (Exception e) {
            log.error("Failed to initialize message:", e);
            sendMessageSafe(session, new TextMessage("{\"error\": \"Invalid JSON format\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(
            @NonNull WebSocketSession session,
            @NonNull CloseStatus closeStatus
    ) {
        String docId = (String) session.getAttributes().get("docId");
        String username = (String) session.getAttributes().get("username");

        if (docId != null) {
            Set<WebSocketSession> sessions = docSessions.get(docId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) docSessions.remove(docId);
            }
        }

        if (username != null) {
            Set<WebSocketSession> sessions = userSessions.get(username);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) userSessions.remove(username);
            }
        }

        log.info("Disconnected. ID: {} | Doc: {}", session.getId(), docId);
    }

    public void forceSyncAll(String docId, SyncMessage message) throws IOException {
        String msg = mapper.writeValueAsString(message);
        broadcastToAll(docId, msg);
    }

    public void updateEffectiveAccessInSession(String username, AccessType access, DocumentAccessPolicy documentAccessPolicy) {
        Set<WebSocketSession> sessions = userSessions.get(username);
        if (sessions == null || sessions.isEmpty()) return;

        AccessType newAccess = access == AccessType.NONE ? documentAccessPolicy.toAccessType() : access;

        for (WebSocketSession session : sessions) {
            try {
                if (newAccess == AccessType.NONE) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                    continue;
                }

                session.getAttributes().put("effectiveAccess", newAccess);
                session.getAttributes().put("explicitAccess", access);
                sendMessageSafe(session, new TextMessage("{\"type\": \"ACCESS_UPDATE\", \"level\": \"" + access + "\"}"));
            } catch (IOException e) {
                log.error("Error updating effective access for user {}", username);
            }
        }
    }

    public void updateEffectiveAccess(String docId, AccessType policyFloor) {
        Set<WebSocketSession> sessions = docSessions.get(docId);
        if (sessions == null) return;

        for (WebSocketSession session : docSessions.getOrDefault(docId, Set.of())) {
            AccessType explicitAccess = (AccessType) session.getAttributes().get("explicitAccess");
            AccessType effectiveAccess = (AccessType) session.getAttributes().get("effectiveAccess");

            if (explicitAccess.ordinal() <= policyFloor.ordinal() && !effectiveAccess.equals(policyFloor)) {
                try {
                    if (policyFloor == AccessType.NONE) {
                        session.close(CloseStatus.POLICY_VIOLATION);
                        continue;
                    }

                    session.getAttributes().put("effectiveAccess", policyFloor);
                    sendMessageSafe(session, new TextMessage("{\"type\": \"ACCESS_UPDATE\", \"level\": \"" + policyFloor + "\"}"));
                } catch (IOException e) {
                    log.error("Error updating session for user {}", session.getAttributes().get("username"));
                }
            }
        }
    }

    private void broadcastToAll(String docId, String message) throws IOException {
        Set<WebSocketSession> sessions = docSessions.get(docId);
        if (sessions == null) return;

        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                sendMessageSafe(session, textMessage);
            }
        }
    }

    private void broadcast(String docId, WebSocketSession sender, String msg) throws IOException {
        Set<WebSocketSession> sessions = docSessions.get(docId);
        if (sessions == null) return;

        for (WebSocketSession session : sessions) {
            if (session.isOpen() && !session.getId().equals(sender.getId())) {
                sendMessageSafe(session, new TextMessage(msg));
            }
        }
    }

    private void sendMessageSafe(WebSocketSession session, TextMessage textMessage) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(textMessage);
            }
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {}

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
