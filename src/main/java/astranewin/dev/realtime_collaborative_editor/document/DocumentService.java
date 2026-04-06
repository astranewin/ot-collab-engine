package astranewin.dev.realtime_collaborative_editor.document;

import astranewin.dev.realtime_collaborative_editor.document.dto.DocumentRequest;
import astranewin.dev.realtime_collaborative_editor.document.dto.DocumentResponse;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentOperationService;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentSyncing;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentWebSocketHandler;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.DocumentSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository repository;
    private final DocumentMapping mapping;
    private final DocumentSnapshotService snapshotService;
    private final DocumentSyncing documentSyncing;
    private final DocumentOperationService operationService;
    private final DocumentWebSocketHandler webSocketHandler;

    public DocumentResponse create(DocumentRequest request) {
        System.out.println(request.name());
        DocumentEntity build = DocumentEntity.builder().name(request.name()).build();
        repository.save(build);

        return mapping.toResponse(build);
    }

    public void revertChanges(Long docId, Long snapshotId) throws IOException {
        String newContent = snapshotService.getSnapshotContent(docId, snapshotId);

        operationService.updateDocument(String.valueOf(docId), newContent);

        SyncMessage syncMessage = documentSyncing.forceSync(newContent, 0);
        webSocketHandler.forceSyncAll(String.valueOf(docId), syncMessage);
    }
}
