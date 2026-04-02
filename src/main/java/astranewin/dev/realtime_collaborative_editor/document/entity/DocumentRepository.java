package astranewin.dev.realtime_collaborative_editor.document.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    boolean existsByName(@NotNull @NotBlank @Size(max = 128) String name);

    @Modifying
    @Query("""
        update DocumentEntity d
        set d.content = :content
        where d.id = :docId
    """)
    void updateContent(Long docId, String content);

    @Query("""
        select d.content from DocumentEntity d
        where d.id = :docId
    """)
    Optional<String> getContentByDocId(Long docId);
}
