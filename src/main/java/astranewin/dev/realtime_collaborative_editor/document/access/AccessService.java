package astranewin.dev.realtime_collaborative_editor.document.access;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.DuplicateAccessException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.InsufficientPermissionsException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.DocumentAccessPolicy;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.AccessResponse;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.HasAccessToDocumentResponse;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.UpdateAccessRequest;
import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentWebSocketHandler;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import astranewin.dev.realtime_collaborative_editor.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessService {
    private final DocumentRepository documentRepository;
    private final AccessRepository repository;
    private final UserRepository userRepository;
    private final AccessMapping mapping;
    private final DocumentWebSocketHandler documentWebSocketHandler;

    @Transactional(readOnly = true)
    public HasAccessToDocumentResponse hasAccessToDocument(Long docId, UserEntity userEntity) {
        DocumentEntity documentEntity = documentRepository.findById(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        AccessType explicit = getExplicitAccess(docId, userEntity.getId());
        AccessType effective = calculateEffectiveAccess(explicit, documentEntity.getAccessPolicy());

        return new HasAccessToDocumentResponse(effective, explicit);
    }

    @Transactional
    public void setAccess(DocumentEntity documentEntity, UserEntity userEntity, AccessType accessType) {
        DocumentAccessEntity accessEntity = repository.findByDocument_IdAndUser_Id(documentEntity.getId(), userEntity.getId())
                .orElse( DocumentAccessEntity.builder().document(documentEntity).user(userEntity).build() );

        accessEntity.setAccess(accessType);
        repository.save(accessEntity);
    }

    @Transactional
    public AccessResponse updateAccess(UserEntity grantorEntity, Long docId, UpdateAccessRequest request) {
        Long granteeId = request.userId();
        AccessType requestedAccessType = request.accessType();

        DocumentEntity documentEntity = documentRepository.findById(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        UserEntity granteeEntity = userRepository.findById(granteeId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        DocumentAccessEntity grantorAccessEntity = repository.findByDocument_IdAndUser_Id(docId, grantorEntity.getId())
                        .orElseThrow(() -> new AccessDeniedException("Access denied"));

        DocumentAccessEntity granteeAccessEntity = repository.findByDocument_IdAndUser_Id(docId, granteeId)
                .orElse(DocumentAccessEntity.builder()
                        .document(documentEntity)
                        .user(granteeEntity)
                        .access(AccessType.NONE)
                        .build()
                );

        if (!hasAccessToPerformAction(grantorAccessEntity.getAccess(), granteeAccessEntity.getAccess(), requestedAccessType))
            throw new InsufficientPermissionsException("You don't have permissions to change access");

        if (requestedAccessType == granteeAccessEntity.getAccess())
            throw new DuplicateAccessException("User already has this access type");


        granteeAccessEntity.setAccess(requestedAccessType);
        DocumentAccessEntity save = repository.save(granteeAccessEntity);

        documentWebSocketHandler.updateEffectiveAccessInSession(
                granteeEntity.getUsername(),
                requestedAccessType,
                documentEntity.getAccessPolicy()
        );

        return mapping.toAccessResponse(save);
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToManage(Long userId, Long docId) {
        AccessType grantedAccess = getExplicitAccess(docId, userId);
        return grantedAccess.isManageAccess();
    }

    private AccessType calculateEffectiveAccess(AccessType explicit, DocumentAccessPolicy policy) {
        if (explicit.isManageAccess()) return explicit;

        if (policy == DocumentAccessPolicy.PUBLIC_EDIT) {
            return AccessType.WRITE;
        }

        if (policy == DocumentAccessPolicy.PUBLIC_READ) {
            return (explicit == AccessType.WRITE) ? AccessType.WRITE : AccessType.READ;
        }

        return explicit;
    }

    private AccessType getExplicitAccess(Long docId, Long userEntity) {
        return repository.findByDocument_IdAndUser_Id(docId, userEntity)
                .map(DocumentAccessEntity::getAccess)
                .orElse(AccessType.NONE);
    }

    private boolean hasAccessToPerformAction(AccessType grantorAccess, AccessType granteeAccess, AccessType targetType) {
        if (granteeAccess == AccessType.OWNER || targetType == AccessType.OWNER) return false;
        if (grantorAccess != AccessType.OWNER && grantorAccess != AccessType.MANAGE) return false;

        if (targetType == AccessType.MANAGE && grantorAccess != AccessType.OWNER) return false;
        if (granteeAccess == AccessType.MANAGE && grantorAccess == AccessType.MANAGE) return false;

        return true;
    }
}
