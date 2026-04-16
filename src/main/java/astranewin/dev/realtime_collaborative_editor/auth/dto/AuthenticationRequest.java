package astranewin.dev.realtime_collaborative_editor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthenticationRequest(
        @NotNull
        @NotBlank
        String username,
        @NotNull
        @NotBlank
        String password
) {
}
