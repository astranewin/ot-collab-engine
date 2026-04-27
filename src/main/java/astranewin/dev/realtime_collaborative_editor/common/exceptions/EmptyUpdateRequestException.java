package astranewin.dev.realtime_collaborative_editor.common.exceptions;

public class EmptyUpdateRequestException extends RuntimeException {
    public EmptyUpdateRequestException(String message) {
        super(message);
    }
}
