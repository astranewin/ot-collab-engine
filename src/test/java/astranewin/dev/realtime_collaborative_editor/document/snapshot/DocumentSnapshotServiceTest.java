package astranewin.dev.realtime_collaborative_editor.document.snapshot;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.domain.DocumentSnapshotEntity;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotListResponse;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentSnapshotServiceTest {
    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentSnapshotRepository documentSnapshotRepository;

    @Mock
    private SnapshotMapping mapping;

    @InjectMocks
    private DocumentSnapshotService underTest;

    @Nested
    class initSnapshot {

        @Test
        void Success() {
            Long docId = 1L;
            LocalDateTime lastSnapshot = LocalDateTime.now().minusDays(1);
            String content = "Test content";
            DocumentEntity documentEntity = new DocumentEntity();

            when(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity));

            underTest.initSnapshot(docId, lastSnapshot, content);

            ArgumentCaptor<DocumentSnapshotEntity> captor = ArgumentCaptor.forClass(DocumentSnapshotEntity.class);
            verify(documentSnapshotRepository).save(captor.capture());

            DocumentSnapshotEntity savedEntity = captor.getValue();
            assertEquals(documentEntity, savedEntity.getDoc());
            assertEquals(lastSnapshot, savedEntity.getEndSnapshot());
            assertEquals(content, savedEntity.getText());
            assertNotNull(savedEntity.getBeginSnapshot());
        }

        @Test
        void DocumentNotFound() {
            Long docId = 1L;

            when(documentRepository.findById(docId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () ->
                    underTest.initSnapshot(docId, LocalDateTime.now(), "content")
            );

            verifyNoInteractions(documentSnapshotRepository);
        }
    }

    @Nested
    class getSnapshots {

        @Test
        void Success() {
            Long docId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            DocumentSnapshotEntity entity = new DocumentSnapshotEntity();
            SnapshotListResponse listResponse = new SnapshotListResponse(1L, LocalDateTime.now(), LocalDateTime.now());

            when(documentSnapshotRepository.findAllByDocId(docId, pageable)).thenReturn(List.of(entity));
            when(mapping.toSnapshotList(entity)).thenReturn(listResponse);

            List<SnapshotListResponse> result = underTest.getSnapshots(docId, pageable);

            assertEquals(1, result.size());
            assertEquals(listResponse, result.getFirst());
        }
    }

    @Nested
    class getSnapshotById {

        @Test
        void Success() {
            Long docId = 1L;
            Long snapshotId = 2L;
            DocumentSnapshotEntity entity = new DocumentSnapshotEntity();
            SnapshotResponse response = new SnapshotResponse(2L, LocalDateTime.now(), LocalDateTime.now(), "content");

            when(documentSnapshotRepository.findByIdAndDocId(snapshotId, docId)).thenReturn(Optional.of(entity));
            when(mapping.toSnapshot(entity)).thenReturn(response);

            SnapshotResponse result = underTest.getSnapshotById(docId, snapshotId);

            assertEquals(response, result);
        }

        @Test
        void SnapshotNotFound() {
            Long docId = 1L;
            Long snapshotId = 2L;

            when(documentSnapshotRepository.findByIdAndDocId(snapshotId, docId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> underTest.getSnapshotById(docId, snapshotId));
        }
    }

    @Nested
    class getSnapshotContent {

        @Test
        void Success() {
            Long docId = 1L;
            Long snapshotId = 2L;
            String expectedContent = "Expected snapshot text";
            DocumentSnapshotEntity entity = DocumentSnapshotEntity.builder()
                    .text(expectedContent)
                    .build();

            when(documentSnapshotRepository.findByIdAndDocId(snapshotId, docId)).thenReturn(Optional.of(entity));

            String result = underTest.getSnapshotContent(docId, snapshotId);

            assertEquals(expectedContent, result);
        }

        @Test
        void SnapshotNotFound() {
            Long docId = 1L;
            Long snapshotId = 2L;

            when(documentSnapshotRepository.findByIdAndDocId(snapshotId, docId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> underTest.getSnapshotContent(docId, snapshotId));
        }
    }
}