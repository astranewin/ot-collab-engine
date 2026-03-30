package astranewin.dev.realtime_collaborative_editor.document.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    boolean existsByName(@NotNull @NotBlank @Size(max = 128) String name);
}
