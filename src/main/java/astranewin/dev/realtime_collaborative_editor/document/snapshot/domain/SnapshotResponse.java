package astranewin.dev.realtime_collaborative_editor.document.snapshot.domain;

import java.time.LocalDateTime;

public record SnapshotResponse(
        Long id,
        LocalDateTime beginDate,
        LocalDateTime endDate,
        String text
) {
}
