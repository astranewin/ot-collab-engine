package astranewin.dev.realtime_collaborative_editor.security;

import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
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

    private static final long ACCESS_TOKEN_TTL = 1000L * 60 * 15; // 15 minutes
    private static final long REFRESH_TOKEN_TTL = 1000L * 60 * 60 * 24 * 7; // 7 days
    private static final long WEBHOOK_TOKEN_TTL = 1000L * 60 * 2; // 2 minutes

    public JwtService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String generateAccessToken(
            UserDetails userDetails
    ) {
        Map<String, Object> extraClaims = Map.of("type", "access");
        Date expiration = new Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL);

        return generateToken(extraClaims, expiration, userDetails.getUsername());
    }

    public String generateWsToken(
            UserDetails userDetails,
            Long docId,
            AccessType accessType
    ) {
        Map<String, Object> extraClaims = Map.of(
                "type", "websocket",
                "docId", docId,
                "accessType", accessType
        );
        Date expiration = new Date(System.currentTimeMillis() + WEBHOOK_TOKEN_TTL);

        return generateToken(extraClaims, expiration, userDetails.getUsername());
    }

    public UserDetails authWebSocketToken(String token) {
        String username = extractUsername(token);
        String tokenType = extractTokenType(token);
        if (username == null || !tokenType.equals("websocket"))
            throw new AccessDeniedException("Access denied");

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        boolean valid = isValid(token, userDetails);
        if (!valid)
            throw new IllegalStateException("Token is not valid");
        return userDetails;
    }

    public String generateRefreshToken(
            UserDetails userDetails
    ) {
        Map<String, Object> extraClaims = Map.of("type", "refresh");
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
        return !("refresh".equals(extractTokenType(token)));
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
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractAccess(String wsToken) {
        return extractClaim(wsToken, s -> s.get("accessType", String.class));
    }
}
