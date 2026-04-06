package astranewin.dev.realtime_collaborative_editor.document.snapshot.domain;

import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Table(name = "snapshots")
public class DocumentSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private DocumentEntity doc;

    @Column(name = "begin_snapshot")
    private LocalDateTime beginSnapshot;
    @Column(name = "end_snapshot")
    private LocalDateTime endSnapshot;

    @Column(columnDefinition = "TEXT", name = "text")
    private String text;

    // Here could be an operation data, that includes user schema, so owner of document could see who made those changes.
}
