package astranewin.dev.realtime_collaborative_editor.common.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        String detailedMessage,
        LocalDateTime createdAt
) {
    public static ErrorResponse of(String message, Exception e) {
        return new ErrorResponse(message, e.getMessage(), LocalDateTime.now());
    }
}
