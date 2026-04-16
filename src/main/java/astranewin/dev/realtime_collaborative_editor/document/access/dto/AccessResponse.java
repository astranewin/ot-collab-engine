package astranewin.dev.realtime_collaborative_editor.document.access.dto;

import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;

public record AccessResponse(
        Long documentId,
        Long userId,
        AccessType accessType
) {
}
