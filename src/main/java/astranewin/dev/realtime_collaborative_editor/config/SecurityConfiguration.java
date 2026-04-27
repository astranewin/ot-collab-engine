package astranewin.dev.realtime_collaborative_editor.config;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.security.JwtAuthenticationFilter;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import astranewin.dev.realtime_collaborative_editor.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfiguration(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider
    ) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/v3/**", "/swagger-ui/**").permitAll()
                        // You can't pass the headers in WebSocket, so to test it, this endpoint is permitted
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(((request, response, authException) ->
                                handlerExceptionResolver.resolveException(request, response, null, authException)))
                        .accessDeniedHandler(((request, response, accessDeniedException) ->
                                handlerExceptionResolver.resolveException(request, response, null, accessDeniedException)))
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            UserEntity userEntity = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            return new UserDetailsImpl(userEntity);
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider (
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider authenticationProvider =
                new DaoAuthenticationProvider(userDetailsService);

        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return authenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
