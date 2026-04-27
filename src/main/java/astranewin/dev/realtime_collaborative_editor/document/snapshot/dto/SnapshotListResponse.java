package astranewin.dev.realtime_collaborative_editor.document.snapshot.dto;

import java.time.LocalDateTime;

public record SnapshotListResponse(
        Long id,
        LocalDateTime beginDate,
        LocalDateTime endDate
) {
}
