package io.github.gagann06.internshiptracker.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    private final String baseUrl;
    private final Duration expiry;

    public PasswordResetService(UserRepository userRepository, UserTokenService userTokenService,
                                PasswordEncoder passwordEncoder, ApplicationEventPublisher applicationEventPublisher,
                                Clock clock, @Value("${app.base-url}") String baseUrl,
                                @Value("${app.password-reset.expiry}") Duration expiry) {
        this.userRepository = userRepository;
        this.userTokenService = userTokenService;
        this.passwordEncoder = passwordEncoder;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.expiry = expiry;
    }

    /** Emails a reset link if the account exists, and silently does nothing if it doesn't. */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String rawToken = userTokenService.issue(user.getId(), UserTokenPurpose.PASSWORD_RESET, expiry);
            String link = baseUrl + "/#reset-password=" + rawToken;
            applicationEventPublisher.publishEvent(new PasswordResetRequested(user.getEmail(), link));
        });
    }

    /**
     * Sets the new password and uses up the token, in one transaction. Opening a link sent to
     * the address also proves the user owns it, so an unverified account becomes verified.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UserToken token = userTokenService.consume(rawToken, UserTokenPurpose.PASSWORD_RESET);
        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidTokenException::new);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        if (!user.isVerified()) {
            user.markVerified(Instant.now(clock));
        }
    }
}
