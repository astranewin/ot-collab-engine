package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.DocumentState;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.OperationType;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
public class DocumentWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DocumentWebSocketHandler.class);

    private final Map<String, Set<WebSocketSession>> docSessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final jakarta.validation.Validator validator;

    private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();
    private final OperationTransformer operationTransformer;
    private final DocumentRepository documentRepository;
    private final DocumentSyncing documentSyncing;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String docId = getDocId(session);
        docSessions.computeIfAbsent(docId, x -> ConcurrentHashMap.newKeySet())
                .add(session);
        documents.computeIfAbsent(docId, id -> {
            String contentFromDb = loadContentFromDb(docId);
            return new DocumentState(contentFromDb, 0, new CopyOnWriteArrayList<>());
        });

        DocumentState doc = documents.get(docId);

        session.sendMessage(new TextMessage(
                mapper.writeValueAsString(Map.of(
                        "type", "init",
                        "content", doc.getContent(),
                        "version", doc.getVersion()
                ))
        ));

        log.info("Connected. ID: {} | Doc: {}", session.getId(), docId);
    }

    @Override
    public void handleMessage(
            @NonNull WebSocketSession session,
            @NonNull WebSocketMessage<?> message
    ) throws Exception {
        String docId = getDocId(session);
        DocumentState doc = documents.get(docId);

        try {
            Operation op = mapper.readValue(message.getPayload().toString(), Operation.class);

            var violations = validator.validate(op);
            if (!violations.isEmpty()) {
                String errorMsg = violations.iterator().next().getMessage();
                log.error("Validation failed for session: {}. Error: {}", session.getId(), errorMsg);
                session.sendMessage(new TextMessage("{\"error\": \"" + errorMsg + "\"}"));
                return;
            }

            int clientVersion = op.getVersion();
            int serverVersion = doc.getVersion();
            List<Operation> history = doc.getHistory();

            log.info("Received op: {}", op);

            if (clientVersion < serverVersion) {
                log.info("Client version is outdated. Syncing...");
                TextMessage sync = documentSyncing.sync(history, clientVersion, serverVersion);
                session.sendMessage(sync);
            }

            Operation updated = operationTransformer.transformAgainst(history, doc.getVersion(), op);

            doc.setContent(apply(doc.getContent(), updated));

            doc.getHistory().add(updated);
            doc.setVersion(doc.getVersion() + 1);

            updated.setVersion(doc.getVersion());
            broadcast(docId, session, mapper.writeValueAsString(updated));
        } catch (Exception e) {
            log.error("Failed to initialize message:", e);
            session.sendMessage(new TextMessage("{\"error\": \"Invalid JSON format\"}"));
        }
    }

    private String apply(String content, Operation op) {
        if (op.getType() == OperationType.INSERT) {
            return applyInsert(content, op);
        } else {
            return applyDelete(content, op);
        }
    }

    private String applyInsert(String content, Operation op) {
        int pos = op.getPosition();
        String text = op.getText();

        return content.substring(0, Math.min(content.length(), pos))
                + text
                + content.substring(Math.min(content.length(), pos));
    }

    private String applyDelete(String content, Operation op) {
        int pos = op.getPosition();
        int len = op.getLength();

        return content.substring(0, pos) + content.substring(pos + len);
    }

    private void broadcast(String docId, WebSocketSession sender, String msg) throws IOException {
        for (WebSocketSession s : docSessions.getOrDefault(docId, Set.of())) {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                s.sendMessage(new TextMessage(msg));
            }
        }
    }

    private String loadContentFromDb(String docId) {
        return "Test document content";
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
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {}

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
