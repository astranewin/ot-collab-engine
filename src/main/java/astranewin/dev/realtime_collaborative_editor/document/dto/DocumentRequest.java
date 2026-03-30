package astranewin.dev.realtime_collaborative_editor.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentRequest(
        @NotNull
        @NotBlank
        @Size(max = 128)
        String name
) {
}
