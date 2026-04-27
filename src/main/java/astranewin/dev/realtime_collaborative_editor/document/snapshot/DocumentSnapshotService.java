package astranewin.dev.realtime_collaborative_editor.document.snapshot;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.domain.DocumentSnapshotEntity;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotListResponse;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentSnapshotService {
    private final DocumentRepository documentRepository;
    private final DocumentSnapshotRepository documentSnapshotRepository;
    private final SnapshotMapping mapping;

    public void initSnapshot(Long docId, LocalDateTime lastSnapshot, String content) {
        DocumentEntity byId = documentRepository.findById(docId)
                .orElseThrow(() -> new NotFoundException("Couldn't find the document"));

        DocumentSnapshotEntity build = DocumentSnapshotEntity.builder()
                .doc(byId)
                .beginSnapshot(LocalDateTime.now())
                .endSnapshot(lastSnapshot)
                .text(content)
                .build();

        documentSnapshotRepository.save(build);
    }

    public List<SnapshotListResponse> getSnapshots(Long docId, Pageable pageable) {
        return documentSnapshotRepository.findAllByDocId(docId, pageable)
                .stream()
                .map(mapping::toSnapshotList)
                .toList();
    }

    public SnapshotResponse getSnapshotById(Long docId, Long snapshotId) {
        DocumentSnapshotEntity snapshot = getSnapshotByIdAndDoc(snapshotId, docId);
        return mapping.toSnapshot(snapshot);
    }

    public String getSnapshotContent(Long docId, Long snapshotId){
        DocumentSnapshotEntity snapshot = getSnapshotByIdAndDoc(snapshotId, docId);
        return snapshot.getText();
    }

    private DocumentSnapshotEntity getSnapshotByIdAndDoc(Long snapshotId, Long docId) {
        return documentSnapshotRepository.findByIdAndDocId(snapshotId, docId)
                .orElseThrow(() -> new NotFoundException("Snapshot not found"));
    }
}
