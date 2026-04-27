package astranewin.dev.realtime_collaborative_editor.document.snapshot;

import astranewin.dev.realtime_collaborative_editor.document.edit.DocumentOperationService;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotListResponse;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doc/{docId}/snapshots")
@RequiredArgsConstructor
public class SnapshotController {
    private final DocumentSnapshotService documentSnapshotService;
    private final DocumentOperationService documentOperationService;

    @GetMapping()
    public List<SnapshotListResponse> getSnapshots(
            Pageable pageable,
            @PathVariable Long docId
    ) {
        return documentSnapshotService.getSnapshots(docId, pageable);
    }

    @GetMapping("/{snapshotId}")
    public SnapshotResponse getSnapshotById(
            @PathVariable Long docId,
            @PathVariable Long snapshotId
    ) {
        return documentSnapshotService.getSnapshotById(docId, snapshotId);
    }
}
