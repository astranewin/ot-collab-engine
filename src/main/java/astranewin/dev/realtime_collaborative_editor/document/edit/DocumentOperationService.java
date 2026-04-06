package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.*;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.DocumentSnapshotService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentOperationService {
    private static final Logger log = LoggerFactory.getLogger(DocumentOperationService.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();
    private final DocumentRepository repository;
    private final OperationTransformer operationTransformer;
    private final DocumentSyncing documentSyncing;
    private final TransactionTemplate transactionTemplate;
    private final DocumentSnapshotService documentSnapshotService;

    public DocumentState initDocument(String docId) {
        if (!repository.existsById(Long.valueOf((docId)))) {
            throw new NotFoundException("Document not found");
        }

        documents.computeIfAbsent(docId, id -> new DocumentState(
                getContentFromDB(Long.valueOf(docId))
        ));

        return documents.get(docId);
    }

    public DocumentHandleOperationResponse handle(String docId, Operation op) {
        DocumentState doc = documents.get(docId);
        DocumentHandleOperationResponse response = new DocumentHandleOperationResponse();

        SyncMessage sync = documentSyncing
                .sync(doc.getHistory(), doc.getContent(), op.getVersion(), doc.getVersion(), doc.getHistoryOffset());
        response.setSyncMessage(sync);
        if (sync != null && sync.getType().equals(SyncType.FULL)) return response;

        Operation updated = processOperation(doc, op);

        response.setOp(updated);
        doc.setDirty(true);

        if (doc.getHistory().size() > 50) {
            log.info("History is too long. Clearing...");
            int removeCount = doc.getHistory().size() - 50;
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

    public void updateDocument(String  docId, String content) {
        repository.updateContent(Long.valueOf(docId), content);

        DocumentState doc = documents.get(docId);
        if (doc == null) return;

        log.info("Updating document with the new data");
        doc.setContent(content);
        doc.getHistory().clear();
        doc.setVersion(0);
    }

    private Operation processOperation(DocumentState doc, Operation op) {
        int newVersion = doc.getVersion() + 1;

        List<Operation> history = doc.getHistory();
        Operation updated = operationTransformer.transformAgainst(history, doc.getVersion(), op, doc.getHistoryOffset());
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
        log.info("flushing data");
        DocumentState doc = documents.get(docId);
        if (doc == null || !doc.isDirty()) return;
        log.info("docId: {} | content: {}", docId, doc.getContent());
        repository.updateContent(Long.valueOf(docId), doc.getContent());
        doc.setDirty(false);
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
}
