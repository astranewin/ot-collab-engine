package astranewin.dev.realtime_collaborative_editor.document;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.EmptyUpdateRequestException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.InsufficientPermissionsException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessService;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import astranewin.dev.realtime_collaborative_editor.document.dto.DocumentRequest;
import astranewin.dev.realtime_collaborative_editor.document.dto.DocumentResponse;
import astranewin.dev.realtime_collaborative_editor.document.dto.UpdateDocumentRequest;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentOperationService;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentSyncing;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentWebSocketHandler;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.DocumentSnapshotService;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AccessService accessService;

    @Transactional
    public DocumentResponse create(DocumentRequest request, UserEntity userEntity) {
        DocumentAccessPolicy accessPolicy = request.access() == null ? DocumentAccessPolicy.RESTRICTED : request.access();

        DocumentEntity build = DocumentEntity.builder()
                .name(request.name())
                .author(userEntity)
                .accessPolicy(accessPolicy)
                .build();

        DocumentEntity save = repository.save(build);

        accessService.setAccess(save, userEntity, AccessType.OWNER);

        return mapping.toResponse(save);
    }

    @Transactional
    public void revertChanges(UserDetailsImpl userDetails, Long docId, Long snapshotId) throws IOException {
        boolean hasAccess = accessService.hasAccessToManage(userDetails.getUserEntity().getId(), docId);
        if (!hasAccess)
            throw new InsufficientPermissionsException("You don't have permissions to revert changes");

        String newContent = snapshotService.getSnapshotContent(docId, snapshotId);
        String documentIdStr = String.valueOf(docId);

        operationService.updateDocument(documentIdStr, newContent);

        SyncMessage syncMessage = documentSyncing.forceSync(newContent, 0);
        webSocketHandler.forceSyncAll(documentIdStr, syncMessage);
    }

    @Transactional
    public DocumentResponse edit(UpdateDocumentRequest request, UserEntity userEntity, Long docId) {
        if (request.name() == null && request.access() == null)
            throw new EmptyUpdateRequestException("No changes provided in request");

        DocumentEntity documentEntity = repository.findById(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        if (!accessService.hasAccessToManage(userEntity.getId(), docId))
            throw new InsufficientPermissionsException("You don't have permissions to edit document settings");

        if (request.name() != null) {
            documentEntity.setName(request.name());
        }

        if (request.access() != null) {
            AccessType policyFloor = request.access().toAccessType();

            documentEntity.setAccessPolicy(request.access());
            webSocketHandler.updateEffectiveAccess(String.valueOf(docId), policyFloor);
        }

        return mapping.toResponse(documentEntity);
    }
}
