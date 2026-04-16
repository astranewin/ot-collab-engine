package astranewin.dev.realtime_collaborative_editor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefreshRequest(
        @NotNull
        @NotBlank
        String refreshToken
) {
}
