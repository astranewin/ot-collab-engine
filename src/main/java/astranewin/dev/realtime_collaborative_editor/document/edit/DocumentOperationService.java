package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.DocumentNotInitializedException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.*;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.DocumentSnapshotService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class DocumentOperationService {
    @Value("${custom.history.size}")
    private int historyLimitSize;

    private static final Logger log = LoggerFactory.getLogger(DocumentOperationService.class);

    private final ScheduledExecutorService scheduler;
    private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();
    private final DocumentRepository repository;
    private final OperationTransformer operationTransformer;
    private final DocumentSyncing documentSyncing;
    private final TransactionTemplate transactionTemplate;
    private final DocumentSnapshotService documentSnapshotService;

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down document operation scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (Exception e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public DocumentState initDocument(String docId) {
        Long parsedDocId = Long.parseLong(docId);
        if (!repository.existsById(parsedDocId)) {
            throw new NotFoundException("Document not found");
        }

        return documents.computeIfAbsent(docId, id -> new DocumentState(getContentFromDB(parsedDocId)));
    }


    public DocumentHandleOperationResponse handle(String docId, Operation op) {
        DocumentState doc = documents.get(docId);

        if (doc == null)
            throw new DocumentNotInitializedException("Document state not initialized");

        synchronized (doc) {
            DocumentHandleOperationResponse response = new DocumentHandleOperationResponse();

            SyncMessage sync = documentSyncing.sync(
                    doc.getHistory(), doc.getContent(), op.getVersion(), doc.getVersion(), doc.getHistoryOffset()
            );
            response.setSyncMessage(sync);

            if (sync != null && sync.getType() == SyncType.FULL)
                return response;

            Operation updated = processOperation(doc, op);
            response.setOp(updated);
            doc.setDirty(true);

            if (doc.getHistory().size() > historyLimitSize) {
                int removeCount = doc.getHistory().size() - historyLimitSize;
                doc.getHistory().subList(0, removeCount).clear();
                doc.setHistoryOffset(doc.getHistoryOffset() + removeCount);

                documentSnapshotService.initSnapshot(Long.valueOf(docId), doc.getLastSnapshot(), doc.getContent());
                doc.setLastSnapshot(LocalDateTime.now());
            }

            if (doc.getFlushTask() != null && !doc.getFlushTask().isDone()) {
                doc.getFlushTask().cancel(false);
            }

            ScheduledFuture<?> schedule = scheduler.schedule(
                    () -> transactionTemplate
                            .executeWithoutResult((status) -> flush(docId)),
                    10,
                    TimeUnit.SECONDS
            );

            doc.setFlushTask(schedule);

            return response;
        }
    }

    @Transactional
    public void updateDocument(String  docId, String content) {
        repository.updateContent(Long.parseLong(docId), content);

        DocumentState doc = documents.get(docId);
        if (doc != null) {
            synchronized (doc) {
                doc.setContent(content);
                doc.getHistory().clear();
                doc.setVersion(0);
                doc.setDirty(false);
            }
        }
    }

    private Operation processOperation(DocumentState doc, Operation op) {
        int newVersion = doc.getVersion() + 1;

        Operation updated = operationTransformer.transformAgainst(doc.getHistory(), op, doc.getHistoryOffset());
        doc.setContent(apply(doc.getContent(), updated));
        doc.getHistory().add(updated);
        doc.setVersion(newVersion);
        updated.setVersion(newVersion);

        return updated;
    }

    protected String getContentFromDB(Long docId) {
        return repository.getContentByDocId(docId)
                .orElse("");
    }

    protected void flush(String docId) {
        DocumentState doc = documents.get(docId);
        if (doc != null && doc.isDirty()) {
            synchronized (doc) {
                log.info("Flushing data from memory into DB");
                repository.updateContent(Long.valueOf(docId), doc.getContent());
                doc.setDirty(false);
            }
        }
    }

    private String apply(String content, Operation op) {
        return op.getType() == OperationType.INSERT
                ? applyInsert(content, op)
                : applyDelete(content, op);
    }

    private String applyInsert(String content, Operation op) {
        int pos = Math.max(0, Math.min(content.length(), op.getPosition()));
        String text = op.getText() != null ? op.getText() : "";

        return new StringBuilder(content)
                .insert(pos, text)
                .toString();
    }

    private String applyDelete(String content, Operation op) {
        int pos = Math.max(0, op.getPosition());
        int len = Math.max(0, op.getLength());

        if (pos >= content.length()) return content;

        int end = Math.min(content.length(), pos + len);

        return new StringBuilder(content)
                .delete(pos, end)
                .toString();
    }
}
