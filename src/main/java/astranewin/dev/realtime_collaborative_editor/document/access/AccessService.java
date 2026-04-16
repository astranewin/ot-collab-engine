package astranewin.dev.realtime_collaborative_editor.document.access;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.DocumentAccessPolicy;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.AccessResponse;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.UpdateAccessRequest;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.security.JwtService;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import astranewin.dev.realtime_collaborative_editor.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessService {
    private static final Logger log = LoggerFactory.getLogger(AccessService.class);
    private final DocumentRepository documentRepository;
    private final AccessRepository repository;
    private final UserRepository userRepository;
    private final AccessMapping mapping;
    private final JwtService jwtService;

    public AccessType hasAccessToDocument(Long docId, UserEntity userEntity) {
        DocumentEntity documentEntity = documentRepository.findById(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        AccessType grantedAccess = repository.findByDocument_IdAndUser_Id(docId, userEntity.getId())
                .map(DocumentAccessEntity::getAccess)
                .orElse(AccessType.NONE);

        DocumentAccessPolicy docAccessPolicy = documentEntity.getAccessPolicy();

        if (docAccessPolicy.equals(DocumentAccessPolicy.PUBLIC_EDIT)) {
            return AccessType.WRITE;
        }

        if (docAccessPolicy.equals(DocumentAccessPolicy.PUBLIC_READ)) {
            return (grantedAccess == AccessType.WRITE) ? AccessType.WRITE :  AccessType.READ;
        }

        return grantedAccess;
    }

    public boolean canEdit(String wsToken) {
        AccessType access = AccessType.valueOf(jwtService.extractAccess(wsToken));

        return !access.equals(AccessType.READ);
    }

    @Transactional
    public AccessResponse setAccess(DocumentEntity documentEntity, UserEntity userEntity, AccessType accessType) {
        DocumentAccessEntity accessEntity = repository.findByDocument_IdAndUser_Id(documentEntity.getId(), userEntity.getId())
                .orElse( DocumentAccessEntity.builder().document(documentEntity).user(userEntity).build() );

        accessEntity.setAccess(accessType);

        DocumentAccessEntity save = repository.save(accessEntity);

        return mapping.toAccessResponse(save);
    }

    @Transactional
    public AccessResponse updateAccess(UserEntity grantorEntity, Long docId, UpdateAccessRequest request) {
        Long granteeId = request.userId();
        AccessType accessType = request.accessType();

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

        boolean hasAccess = hasAccessToPerformAction(
                grantorAccessEntity.getAccess(), granteeAccessEntity.getAccess(), accessType
        );

        if (!hasAccess)
            throw new AccessDeniedException("Access denied");

        if (accessType.equals(granteeAccessEntity.getAccess()))
            throw new IllegalStateException("Duplicate access type");

        granteeAccessEntity.setAccess(accessType);

        DocumentAccessEntity save = repository.save(granteeAccessEntity);

        return mapping.toAccessResponse(save);
    }

    private boolean hasAccessToPerformAction(AccessType grantorAccess, AccessType granteeAccess, AccessType calledType) {
        if (granteeAccess.equals(AccessType.OWNER) || calledType.equals(AccessType.OWNER)) return false;

        if (calledType.equals(AccessType.MANAGE) && !grantorAccess.equals(AccessType.OWNER)) return false;
        if (granteeAccess.equals(AccessType.MANAGE) && grantorAccess.equals(AccessType.MANAGE)) return false;

        // Add check for availability to change access on document by the readers/editors

        return true;
    }
}
