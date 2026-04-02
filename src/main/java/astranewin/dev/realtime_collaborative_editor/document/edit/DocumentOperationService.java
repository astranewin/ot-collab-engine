package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.*;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

        List<Operation> history = doc.getHistory();

        SyncMessage sync = documentSyncing
                .sync(doc.getHistory(), doc.getContent(), op.getVersion(), doc.getVersion());
        response.setSyncMessage(sync);
        if (sync.getType().equals(SyncType.FULL)) return response;

        Operation updated = operationTransformer.transformAgainst(history, doc.getVersion(), op);

        doc.setContent(apply(doc.getContent(), updated));

        doc.getHistory().add(updated);

        int newVersion = doc.getVersion() + 1;

        doc.setVersion(newVersion);
        updated.setVersion(newVersion);
        response.setOp(updated);

        doc.setDirty(true);

        if (doc.getHistory().size() > 100) doc.getHistory().removeFirst();

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
