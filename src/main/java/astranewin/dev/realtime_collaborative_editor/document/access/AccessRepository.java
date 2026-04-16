package astranewin.dev.realtime_collaborative_editor.document.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessRepository extends JpaRepository<DocumentAccessEntity, Long> {
    Optional<DocumentAccessEntity> findByDocument_IdAndUser_Id(Long docId, Long userId);

    boolean existsByDocument_IdAndUser_Id(Long documentId, Long userId);
}
