package astranewin.dev.realtime_collaborative_editor.auth.token;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.JwtExpiredException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.JwtValidationException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.security.JwtService;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final RefreshTokenRepository refreshTokenRepository;

    public String create(UserEntity userEntity) {
        String token = jwtService.generateRefreshToken(new UserDetailsImpl(userEntity));
        String hashedToken = tokenHashService.hashToken(token);

        RefreshTokenEntity build = RefreshTokenEntity.builder()
                .token(hashedToken)
                .user(userEntity)
                .build();

        refreshTokenRepository.save(build);
        return token;
    }

    @Transactional(readOnly = true)
    public void validate(String token) {
        String hashed = verifyAndHashToken(token);

        if (!refreshTokenRepository.existsByToken(hashed)) {
            throw new NotFoundException("Token not found");
        }
    }

    @Transactional
    public void deleteByToken(String token) {
        String hashed = verifyAndHashToken(token);

        int deleted = refreshTokenRepository.deleteByToken(hashed);
        if (deleted == 0)
            throw new NotFoundException("Token not found");
    }

    public void deleteAllUserTokens(String token) {
        verifyTokenState(token);
        String username = jwtService.extractUsername(token);
        refreshTokenRepository.deleteAllByUserUsername(username);
    }

    private String verifyAndHashToken(String token) {
        verifyTokenState(token);
        return tokenHashService.hashToken(token);
    }

    private void verifyTokenState(String token) {
        if (jwtService.isNotTypeRefresh(token)) {
            throw new JwtValidationException("The provided JWT type is not valid");
        }
        if (jwtService.isExpired(token)) {
            throw new JwtExpiredException("The provided JWT is expired");
        }
    }
}
