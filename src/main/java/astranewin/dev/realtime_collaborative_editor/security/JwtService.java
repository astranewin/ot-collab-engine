package astranewin.dev.realtime_collaborative_editor.security;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.JwtExpiredException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.JwtValidationException;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    private final UserDetailsService userDetailsService;
    @Value("${custom.jwt_secret}")
    private String jwtSecret;

    private SecretKey cachedSecretKey;

    @Value("${custom.jwt.expiration.access}")
    private long ACCESS_TOKEN_TTL;
    @Value("${custom.jwt.expiration.refresh}")
    private long REFRESH_TOKEN_TTL;
    @Value("${custom.jwt.expiration.websocket}")
    private long WEBHOOK_TOKEN_TTL;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        this.cachedSecretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public JwtService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String generateAccessToken(
            UserDetails userDetails
    ) {
        Map<String, Object> extraClaims = Map.of("type", TokenType.ACCESS.getValue());
        Date expiration = new Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL);

        return generateToken(extraClaims, expiration, userDetails.getUsername());
    }

    public String generateWsToken(
            UserDetails userDetails,
            Long docId,
            AccessType effectiveAccess,
            AccessType explicitAccess
    ) {
        Map<String, Object> extraClaims = Map.of(
                "type", TokenType.WEBSOCKET.getValue(),
                "docId", docId,
                "effectiveAccess", effectiveAccess,
                "explicitAccess", explicitAccess
        );
        Date expiration = new Date(System.currentTimeMillis() + WEBHOOK_TOKEN_TTL);

        return generateToken(extraClaims, expiration, userDetails.getUsername());
    }

    public UserDetails authWebSocketToken(String token) {
        Claims claims = extractAllClaims(token);

        String tokenType = claims.get("type", String.class);
        String username = claims.getSubject();

        if (username == null) {
            throw new JwtValidationException("JWT does not contain a subject");
        }

        if (!tokenType.equals(TokenType.WEBSOCKET.getValue()))
            throw new JwtValidationException("The provided JWT type is not valid");

        return userDetailsService.loadUserByUsername(username);
    }

    public String validateAccessTokenAndGetUsername(String token) {
        Claims claims = extractAllClaims(token);

        String tokenType = claims.get("type", String.class);
        if (!tokenType.equals(TokenType.ACCESS.getValue()))
            throw new JwtValidationException("The provided JWT type is not valid");

        String username = claims.getSubject();
        if (username == null)
            throw new JwtValidationException("Token does not contain a username");

        return username;
    }

    public String generateRefreshToken(
            UserDetails userDetails
    ) {
        Map<String, Object> extraClaims = Map.of("type", TokenType.REFRESH.getValue());
        Date expiration = new Date(System.currentTimeMillis() + REFRESH_TOKEN_TTL);

        return generateToken(extraClaims, expiration, userDetails.getUsername());
    }

    private String generateToken(Map<String, Object> extraClaim, Date expiration, String username) {
        return Jwts
                .builder()
                .claims()
                .add(extraClaim)
                .and()
                .signWith(getSignInKey())
                .issuedAt(new Date())
                .expiration(expiration)
                .subject(username)
                .compact();
    }

    public boolean isNotTypeRefresh(String token) {
        return !(TokenType.REFRESH.getValue().equals(extractTokenType(token)));
    }

    public boolean isValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isExpired(token);
    }

    public boolean isExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, s -> s.get("type", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts
                    .parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtExpiredException("The provided JWT has expired", e);
        } catch (MalformedJwtException | SignatureException e) {
            throw new JwtValidationException("The provided JWT is invalid or corrupted", e);
        } catch (JwtException e) {
            throw new JwtValidationException("An error occurred while parsing JWT", e);
        }
    }

    private SecretKey getSignInKey() {
        return this.cachedSecretKey;
    }

    public String extractEffective(String wsToken) {
        return extractClaim(wsToken, s -> s.get("effectiveAccess", String.class));
    }

    public String extractExplicitAccess(String wsToken) {
        return extractClaim(wsToken, s -> s.get("explicitAccess", String.class));
    }
}
