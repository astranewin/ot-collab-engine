package astranewin.dev.realtime_collaborative_editor.common.exceptions;

import astranewin.dev.realtime_collaborative_editor.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> exceptionHandler(
            Exception e
    ) {
        log.error("exceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Internal server error", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFoundExceptionHandler(
            NotFoundException e
    ) {
        log.error("notFoundExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Not found", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    @ExceptionHandler(exception = {
            JwtValidationException.class,
            JwtExpiredException.class
    })
    public ResponseEntity<ErrorResponse> jwtValidationExceptionHandler(
            Exception e
    ) {
        log.error("jwtValidationExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("JWT validation failed", e);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(DuplicateAccessException.class)
    public ResponseEntity<ErrorResponse> duplicateAccessExceptionHandler(
            Exception e
    ) {
        log.error("duplicateAccessExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Duplicated access", e);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> duplicateResourceExceptionHandler(
            Exception e
    ) {
        log.error("duplicateResourceExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Duplicate resource", e);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    @ExceptionHandler(EmptyUpdateRequestException.class)
    public ResponseEntity<ErrorResponse> emptyUpdateRequestExceptionHandler(
            Exception e
    ) {
        log.error("emptyUpdateRequestExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Empty update request", e);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(DocumentNotInitializedException.class)
    public ResponseEntity<ErrorResponse> documentNotInitializedExceptionHandler(
            Exception e
    ) {
        log.error("documentNotInitializedExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Document have not initialized", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    @ExceptionHandler(InsufficientPermissionsException.class)
    public ResponseEntity<ErrorResponse> insufficientPermissionsExceptionHandler(
            Exception e
    ) {
        log.error("insufficientPermissionsExceptionHandler: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("Insufficient required permissions", e);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }
}
