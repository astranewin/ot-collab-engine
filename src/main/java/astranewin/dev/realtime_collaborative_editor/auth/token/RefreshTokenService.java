package astranewin.dev.realtime_collaborative_editor.auth.token;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.security.JwtService;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final RefreshTokenRepository refreshTokenRepository;

    public String create(UserEntity userEntity) {
        UserDetails userDetails = new UserDetailsImpl(userEntity);

        String token = jwtService.generateRefreshToken(userDetails);
        String hashedToken = tokenHashService.hashToken(token);

        RefreshTokenEntity build = RefreshTokenEntity.builder()
                .token(hashedToken)
                .user(userEntity)
                .build();

        refreshTokenRepository.save(build);
        return token;
    }

    public void validate(String token) {
        if (jwtService.isNotTypeRefresh(token))
            throw new IllegalStateException("Invalid token state");

        if (jwtService.isExpired(token))
            throw new AccessDeniedException("Token is not valid");

        String hashed = tokenHashService.hashToken(token);
        refreshTokenRepository.findByToken(hashed)
                .orElseThrow(() -> new NotFoundException("Token not found"));
    }

    public void deleteByToken(String token) {
        if (jwtService.isNotTypeRefresh(token))
            throw new IllegalStateException("Invalid token state");

        if (jwtService.isExpired(token))
            throw new AccessDeniedException("Token is not valid");

        String hashed = tokenHashService.hashToken(token);
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(hashed)
                .orElseThrow(() -> new NotFoundException("Token not found"));

        refreshTokenRepository.delete(tokenEntity);
    }

    public void deleteAllUserTokens(String token) {
        if (jwtService.isNotTypeRefresh(token))
            throw new IllegalStateException("Invalid token state");

        if (jwtService.isExpired(token))
            throw new AccessDeniedException("Token is not valid");

        String username = jwtService.extractUsername(token);
        refreshTokenRepository.deleteAllByUserUsername(username);
    }
}
