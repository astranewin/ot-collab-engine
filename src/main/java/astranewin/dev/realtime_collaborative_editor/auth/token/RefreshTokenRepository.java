package astranewin.dev.realtime_collaborative_editor.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    void deleteAllByUserUsername(String username);

    boolean existsByToken(String token);

    int deleteByToken(String hashed);
}
