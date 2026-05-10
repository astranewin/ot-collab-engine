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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AccessRepository accessRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessMapping mapping;
    @Mock
    private DocumentWebSocketHandler documentWebSocketHandler;

    @InjectMocks
    private AccessService underTest;

    private UserEntity grantor;
    private UserEntity grantee;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        grantor = new UserEntity();
        grantor.setId(1L);
        grantor.setUsername("grantorUser");

        grantee = new UserEntity();
        grantee.setId(2L);
        grantee.setUsername("granteeUser");

        document = new DocumentEntity();
        document.setId(100L);
        document.setAccessPolicy(DocumentAccessPolicy.RESTRICTED);
    }

    @Nested
    class hasAccessToDocument {
        @Test
        void DocumentNotFound_ThrowsNotFoundException() {
            when(documentRepository.findById(100L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> underTest.hasAccessToDocument(100L, grantor));
        }

        @Test
        void PublicEditPolicy_ReturnsWriteEffective() {
            document.setAccessPolicy(DocumentAccessPolicy.PUBLIC_EDIT);
            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, 1L)).thenReturn(Optional.empty());

            HasAccessToDocumentResponse response = underTest.hasAccessToDocument(100L, grantor);

            assertEquals(AccessType.WRITE, response.getEffectiveAccess());
            assertEquals(AccessType.NONE, response.getExplicitAccess());
        }

        @Test
        void ExplicitManage_OverridesPolicy() {
            document.setAccessPolicy(DocumentAccessPolicy.PUBLIC_READ);

            DocumentAccessEntity accessEntity = DocumentAccessEntity.builder().access(AccessType.MANAGE).build();
            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, 1L)).thenReturn(Optional.of(accessEntity));

            HasAccessToDocumentResponse response = underTest.hasAccessToDocument(100L, grantor);

            assertEquals(AccessType.MANAGE, response.getEffectiveAccess());
            assertEquals(AccessType.MANAGE, response.getExplicitAccess());
        }
    }

    @Nested
    class setAccess {
        @Test
        void ExistingAccess_UpdatesAndSaves() {
            DocumentAccessEntity existingAccess = DocumentAccessEntity.builder()
                    .document(document).user(grantee).access(AccessType.READ).build();

            when(accessRepository.findByDocument_IdAndUser_Id(document.getId(), grantee.getId()))
                    .thenReturn(Optional.of(existingAccess));

            underTest.setAccess(document, grantee, AccessType.WRITE);

            assertEquals(AccessType.WRITE, existingAccess.getAccess());
            verify(accessRepository).save(existingAccess);
        }

        @Test
        void NoExistingAccess_CreatesAndSaves() {
            when(accessRepository.findByDocument_IdAndUser_Id(document.getId(), grantee.getId()))
                    .thenReturn(Optional.empty());

            underTest.setAccess(document, grantee, AccessType.READ);

            ArgumentCaptor<DocumentAccessEntity> captor = ArgumentCaptor.forClass(DocumentAccessEntity.class);
            verify(accessRepository).save(captor.capture());

            DocumentAccessEntity savedEntity = captor.getValue();
            assertEquals(AccessType.READ, savedEntity.getAccess());
            assertEquals(document, savedEntity.getDocument());
            assertEquals(grantee, savedEntity.getUser());
        }

    }

    @Nested
    class updateAccess {

        @Test
        void SuccessfulUpdate() {
            UpdateAccessRequest request = new UpdateAccessRequest(grantee.getId(), AccessType.WRITE);

            DocumentAccessEntity grantorAccess = DocumentAccessEntity.builder().access(AccessType.OWNER).build();
            DocumentAccessEntity granteeAccess = DocumentAccessEntity.builder().access(AccessType.READ).user(grantee).build();
            AccessResponse mockResponse = new AccessResponse(2L, 1L, AccessType.WRITE);

            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(userRepository.findById(grantee.getId())).thenReturn(Optional.of(grantee));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantor.getId())).thenReturn(Optional.of(grantorAccess));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantee.getId())).thenReturn(Optional.of(granteeAccess));
            when(accessRepository.save(any(DocumentAccessEntity.class))).thenReturn(granteeAccess);
            when(mapping.toAccessResponse(granteeAccess)).thenReturn(mockResponse);

            AccessResponse result = underTest.updateAccess(grantor, 100L, request);

            assertEquals(mockResponse, result);
            verify(accessRepository).save(granteeAccess);
            verify(documentWebSocketHandler).updateEffectiveAccessInSession(
                    eq(grantee.getUsername()), eq(AccessType.WRITE), eq(document.getAccessPolicy())
            );
        }

        @Test
        void GrantorHasNoAccess_ThrowsAccessDenied() {
            UpdateAccessRequest request = new UpdateAccessRequest(grantee.getId(), AccessType.WRITE);

            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(userRepository.findById(grantee.getId())).thenReturn(Optional.of(grantee));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantor.getId())).thenReturn(Optional.empty());

            assertThrows(AccessDeniedException.class, () -> underTest.updateAccess(grantor, 100L, request));
        }

        @Test
        void InsufficientPermissions_ThrowsException() {
            UpdateAccessRequest request = new UpdateAccessRequest(grantee.getId(), AccessType.MANAGE);

            DocumentAccessEntity grantorAccess = DocumentAccessEntity.builder().access(AccessType.MANAGE).build();
            DocumentAccessEntity granteeAccess = DocumentAccessEntity.builder().access(AccessType.READ).build();

            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(userRepository.findById(grantee.getId())).thenReturn(Optional.of(grantee));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantor.getId())).thenReturn(Optional.of(grantorAccess));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantee.getId())).thenReturn(Optional.of(granteeAccess));

            assertThrows(InsufficientPermissionsException.class, () -> underTest.updateAccess(grantor, 100L, request));
        }

        @Test
        void DuplicateAccess_ThrowsException() {
            UpdateAccessRequest request = new UpdateAccessRequest(grantee.getId(), AccessType.WRITE);

            DocumentAccessEntity grantorAccess = DocumentAccessEntity.builder().access(AccessType.OWNER).build();
            DocumentAccessEntity granteeAccess = DocumentAccessEntity.builder().access(AccessType.WRITE).build();

            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(userRepository.findById(grantee.getId())).thenReturn(Optional.of(grantee));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantor.getId())).thenReturn(Optional.of(grantorAccess));
            when(accessRepository.findByDocument_IdAndUser_Id(100L, grantee.getId())).thenReturn(Optional.of(granteeAccess));

            assertThrows(DuplicateAccessException.class, () -> underTest.updateAccess(grantor, 100L, request));
        }
    }

    @Test
    void hasAccessToManage_ReturnsTrueForManageOrOwner() {
        DocumentAccessEntity accessEntity = DocumentAccessEntity.builder().access(AccessType.MANAGE).build();
        when(accessRepository.findByDocument_IdAndUser_Id(100L, grantor.getId())).thenReturn(Optional.of(accessEntity));

        assertTrue(underTest.hasAccessToManage(grantor.getId(), 100L));
    }

    @Test
    void hasAccessToManage_ReturnsFalseForReadOrWrite() {
        DocumentAccessEntity accessEntity = DocumentAccessEntity.builder().access(AccessType.READ).build();
        when(accessRepository.findByDocument_IdAndUser_Id(100L, grantor.getId())).thenReturn(Optional.of(accessEntity));

        assertFalse(underTest.hasAccessToManage(grantor.getId(), 100L));
    }
}