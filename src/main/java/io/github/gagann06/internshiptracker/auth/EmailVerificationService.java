package io.github.gagann06.internshiptracker.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final String baseUrl;
    private final Duration expiry;

    public EmailVerificationService(UserRepository userRepository, UserTokenService userTokenService, Clock clock, ApplicationEventPublisher applicationEventPublisher, @Value("${app.base-url}") String baseUrl, @Value("${app.verification.expiry}") Duration expiry) {
        this.userRepository = userRepository;
        this.userTokenService = userTokenService;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
        this.baseUrl = baseUrl;
        this.expiry = expiry;
    }

    @Transactional
    public void sendVerificationEmail(User user) {
        String rawToken = userTokenService.issue(user.getId(), UserTokenPurpose.EMAIL_VERIFICATION, expiry);
        String link = baseUrl + "/#verify-email=" + rawToken;
        applicationEventPublisher.publishEvent(new VerificationEmailRequested(user.getEmail(), link));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UserToken token = userTokenService.consume(rawToken, UserTokenPurpose.EMAIL_VERIFICATION);
        userRepository.findById(token.getUserId())
                .orElseThrow(InvalidTokenException::new)
                .markVerified(Instant.now(clock));
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        String trimmedEmail = email.strip().toLowerCase();
        Optional<User> found = userRepository.findByEmailIgnoreCase(trimmedEmail);

        if (found.isPresent() && !found.get().isVerified()) {
            sendVerificationEmail(found.get());
        }
    }
}