package astranewin.dev.realtime_collaborative_editor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LogoutRequest(
        @NotNull
        @NotBlank
        String refreshToken
) {
}
