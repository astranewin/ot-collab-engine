package astranewin.dev.realtime_collaborative_editor.document.access.dto;

import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateAccessRequest(
        @NotNull
        @Positive
        Long userId,
        @NotNull
        AccessType accessType
) {
}
