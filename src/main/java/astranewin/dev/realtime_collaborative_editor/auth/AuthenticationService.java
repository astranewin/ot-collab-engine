package astranewin.dev.realtime_collaborative_editor.auth;

import astranewin.dev.realtime_collaborative_editor.auth.dto.*;
import astranewin.dev.realtime_collaborative_editor.auth.token.RefreshTokenService;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.DuplicateResourceException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.InsufficientPermissionsException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessService;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.HasAccessToDocumentResponse;
import astranewin.dev.realtime_collaborative_editor.security.JwtService;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import astranewin.dev.realtime_collaborative_editor.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AccessService accessService;

    public AuthenticationResponse register(
            AuthenticationRequest request
    ) {
        if (userRepository.existsByUsername(request.username()))
            throw new DuplicateResourceException("Username is already taken");

        UserEntity build = UserEntity.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        UserEntity save = userRepository.save(build);

        return generateAuthTokens(save);
    }

    public AuthenticationResponse login(
            AuthenticationRequest request
    ) {
        Authentication authenticate = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        UserDetailsImpl principal = (UserDetailsImpl) authenticate.getPrincipal();

        if (principal == null)
            throw new IllegalStateException("Provider returned an unexpected principal type");

        return generateAuthTokens(principal.getUserEntity());
    }

    public AuthenticationResponse refresh(
            RefreshRequest request
    ) {
        refreshTokenService.validate(request.refreshToken());

        String username = jwtService.extractUsername(request.refreshToken());
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return refreshAuthTokens(userEntity, request.refreshToken());
    }

    public void logout(
            LogoutRequest request
    ) {
        refreshTokenService.deleteByToken(request.refreshToken());
    }

    public void logoutAll(
            LogoutRequest request
    ) {
        refreshTokenService.deleteAllUserTokens(request.refreshToken());
    }

    public WebSocketResponse webSocketAuth(UserDetailsImpl userDetails, Long docId) {
        HasAccessToDocumentResponse accessType = accessService
                .hasAccessToDocument(docId, userDetails.getUserEntity());

        if (accessType.getEffectiveAccess() == AccessType.NONE)
            throw new InsufficientPermissionsException("You are not able to open the document");

        String token = jwtService.generateWsToken(
                userDetails, docId, accessType.getEffectiveAccess(), accessType.getExplicitAccess()
        );
        return new WebSocketResponse(token);
    }

    private AuthenticationResponse generateAuthTokens(UserEntity userEntity) {
        String refresh = refreshTokenService.create(userEntity);
        String access = jwtService.generateAccessToken(new UserDetailsImpl(userEntity));
        return new AuthenticationResponse(access, refresh);
    }

    private AuthenticationResponse refreshAuthTokens(UserEntity userEntity, String refresh) {
        refreshTokenService.deleteByToken(refresh);

        String access = jwtService.generateAccessToken(new UserDetailsImpl(userEntity));
        String newRefresh = refreshTokenService.create(userEntity);

        return new AuthenticationResponse(access, newRefresh);
    }
}
