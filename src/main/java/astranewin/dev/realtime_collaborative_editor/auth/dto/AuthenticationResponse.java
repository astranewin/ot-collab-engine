package astranewin.dev.realtime_collaborative_editor.auth.dto;

public record AuthenticationResponse(
        String accessToken,
        String refreshToken
) {
}
