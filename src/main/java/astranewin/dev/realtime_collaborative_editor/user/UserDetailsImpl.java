package astranewin.dev.realtime_collaborative_editor.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {
    private UserEntity userEntity;

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public @Nullable String getPassword() {
        return userEntity.getPasswordHash();
    }

    @Override
    @NonNull
    public String getUsername() {
        return userEntity.getUsername();
    }
}
