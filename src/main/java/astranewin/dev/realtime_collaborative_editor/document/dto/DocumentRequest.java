package astranewin.dev.realtime_collaborative_editor.document.dto;

import astranewin.dev.realtime_collaborative_editor.document.DocumentAccessPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentRequest(
        @NotNull
        @NotBlank
        @Size(max = 128)
        String name,
        DocumentAccessPolicy access
) {
}
