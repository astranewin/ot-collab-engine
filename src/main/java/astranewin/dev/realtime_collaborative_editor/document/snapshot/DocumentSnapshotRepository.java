package astranewin.dev.realtime_collaborative_editor.document.snapshot;

import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.domain.DocumentSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshotEntity, Long> {
    List<DocumentSnapshotEntity> findAllByDocId(Long docId, Pageable pageable);

    Optional<DocumentSnapshotEntity> findByIdAndDocId(Long snapshotId, Long docId);

    Long doc(DocumentEntity doc);
}
