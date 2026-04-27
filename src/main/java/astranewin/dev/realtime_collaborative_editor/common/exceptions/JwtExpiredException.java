package astranewin.dev.realtime_collaborative_editor.common.exceptions;

public class JwtExpiredException extends JwtValidationException {
    public JwtExpiredException(String message) {
      super(message);
    }
    public JwtExpiredException(String message, Throwable cause) {
      super(message, cause);
    }
}
