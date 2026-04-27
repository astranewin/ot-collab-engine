package astranewin.dev.realtime_collaborative_editor.auth;

import astranewin.dev.realtime_collaborative_editor.auth.dto.*;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService service;

    @PostMapping("/register")
    public AuthenticationResponse register(
            @RequestBody @Valid AuthenticationRequest request
    ) {
        return service.register(request);
    }

    @PostMapping("/login")
    public AuthenticationResponse login(
            @RequestBody @Valid AuthenticationRequest request
    ) {
        return service.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestBody @Valid LogoutRequest request
    ) {
        service.logout(request);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(
            @RequestBody @Valid LogoutRequest request
    ) {
        service.logoutAll(request);
    }

    @PostMapping("/refresh")
    public AuthenticationResponse refresh(
            @RequestBody @Valid RefreshRequest request
    ) {
        return service.refresh(request);
    }

    @Operation(
            summary = "Get websocket token",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/ws/{docId}")
    public WebSocketResponse webSocket(
            @PathVariable Long docId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return service.webSocketAuth(userDetails, docId);
    }
}
