package astranewin.dev.realtime_collaborative_editor.document.dto;

import astranewin.dev.realtime_collaborative_editor.document.DocumentAccessPolicy;
import jakarta.annotation.Nullable;

public record UpdateDocumentRequest(
        @Nullable
        String name,
        @Nullable
        DocumentAccessPolicy access
) {
}
