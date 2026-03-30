package astranewin.dev.realtime_collaborative_editor.document.edit;

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
    private final DocumentRepository documentRepository;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String docId = getDocId(session);
        docSessions.computeIfAbsent(docId, x -> ConcurrentHashMap.newKeySet())
                .add(session);
        documents.computeIfAbsent(docId, id -> {
//            String contentFromDb = documentRepository.findById(Long.getLong(id));
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
            log.info("Received op: {}", op);

            // todo: if user version is old, we have to send all actions, so client will use them to represent all the previous actions.

            var violations = validator.validate(op);
            if (!violations.isEmpty()) {
                String errorMsg = violations.iterator().next().getMessage();
                log.error("Validation failed for session: {}. Error: {}", session.getId(), errorMsg);
                session.sendMessage(new TextMessage("{\"error\": \"" + errorMsg + "\"}"));
                return;
            }

            List<Operation> missed = doc.getHistory().subList(
                    op.getVersion(),
                    doc.getVersion()
            );

            for (Operation prevOp : missed) {
                op = transform(op, prevOp);
            }

            doc.setContent(apply(doc.getContent(), op));

            doc.getHistory().add(op);
            doc.setVersion(doc.getVersion() + 1);

            op.setVersion(doc.getVersion());
            System.out.println(doc.getContent());
            broadcast(docId, session, mapper.writeValueAsString(op));
        } catch (Exception e) {
            log.error("Failed to initialize message:", e);
            session.sendMessage(new TextMessage("{\"error\": \"Invalid JSON format\"}"));
        }
    }

    private Operation transform(Operation incoming, Operation applied) {
        if (incoming.getType().equals(OperationType.INSERT) &&
                applied.getType().equals(OperationType.INSERT)) {
            // Insert –> Insert
            return transformInsertInsert(incoming, applied);
        }

        if (incoming.getType().equals(OperationType.INSERT) &&
                applied.getType().equals(OperationType.DELETE)) {
            // Insert -> Delete
            return transformInsertDelete(incoming, applied);
        }

        if (incoming.getType().equals(OperationType.DELETE) &&
                applied.getType().equals(OperationType.INSERT)) {
            // Delete -> Insert
            return transformDeleteInsert(incoming, applied);
        }

        // Delete -> Delete
        return transformDeleteDelete(incoming, applied);
    }

    private Operation transformInsertInsert(Operation a, Operation b) {
        if (a.getPosition() < b.getPosition()) return a;
        if (a.getPosition() > b.getPosition()) {
            a.setPosition(a.getPosition() + b.getText().length());
            return a;
        }

        if (a.getSenderId().compareTo(b.getSenderId()) > 0) {
            a.setPosition(a.getPosition() + b.getText().length());
        }

        a.setPosition(a.getPosition() + b.getText().length());
        return a;
    }

    private Operation transformInsertDelete(Operation insert, Operation delete) {
        int delStart = delete.getPosition();
        int delEnd = delStart + delete.getLength();

        if (insert.getPosition() <= delStart) return insert;
        if (insert.getPosition() >= delEnd) {
            insert.setPosition(insert.getPosition() - delete.getLength());
            return insert;
        }

        insert.setPosition(delStart);
        return insert;
    }

    private Operation transformDeleteInsert(Operation delete, Operation insert) {
        if (insert.getPosition() <= delete.getPosition()) {
            delete.setPosition(delete.getPosition() + insert.getText().length());
            return delete;
        }

        if (insert.getPosition() >= delete.getPosition() + delete.getLength()) return delete;

        delete.setLength(delete.getLength() + insert.getText().length());
        return delete;
    }

    private Operation transformDeleteDelete(Operation a, Operation b) {
        int aStart = a.getPosition();
        int aEnd = aStart + a.getLength();

        int bStart = b.getPosition();
        int bEnd = bStart + b.getLength();

        if (aEnd <= bStart) return a;
        if (aStart >= bEnd) {
            a.setPosition(aStart - b.getLength());
            return a;
        }

        int overlapStart = Math.max(aStart, bStart);
        int overlapEnd = Math.min(aEnd, bEnd);
        int overlap = overlapEnd - overlapStart;

        a.setLength(a.getLength() - overlap);

        if (bStart < aStart) {
            a.setPosition(aStart - (overlapStart - bStart));
        }

        return a;
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

        log.info("pos: {}, text: {}, content: {}", pos, text, content);

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
    ) throws Exception {
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
