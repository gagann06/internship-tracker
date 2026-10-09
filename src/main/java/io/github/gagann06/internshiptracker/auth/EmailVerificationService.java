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
    private final UserTokenRepository userTokenRepository;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;
    
    private final String baseUrl;
    private final Duration expiry;
    
    public EmailVerificationService(UserRepository userRepository, UserTokenRepository userTokenRepository, Clock clock, ApplicationEventPublisher applicationEventPublisher, @Value("${app.base-url}") String baseUrl, @Value("${app.verification.expiry}") Duration expiry) {
        this.userRepository = userRepository;
        this.userTokenRepository = userTokenRepository;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
        this.baseUrl = baseUrl;
        this.expiry = expiry;
    }

    @Transactional
    public void sendVerificationEmail(User user) {
        Instant now = Instant.now(clock);

        userTokenRepository.deleteUnusedTokensForUserAndPurpose(user.getId(), UserTokenPurpose.EMAIL_VERIFICATION);
        String rawToken = OneTimeTokens.generate();
        UserToken newToken = new UserToken(user.getId(), UserTokenPurpose.EMAIL_VERIFICATION, OneTimeTokens.hash(rawToken), now.plus(expiry));
        userTokenRepository.save(newToken);

        String link = baseUrl + "/#verify-email=" + rawToken;
        applicationEventPublisher.publishEvent(new VerificationEmailRequested(user.getEmail(), link));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = Instant.now(clock);
        UserToken token = userTokenRepository.findByTokenHashAndPurpose(OneTimeTokens.hash(rawToken), UserTokenPurpose.EMAIL_VERIFICATION)
                            .orElseThrow(InvalidVerificationTokenException::new);
        
        if (token.isUsable(now)) {
            token.markUsed(now);
            userRepository.findById(token.getUserId()).orElseThrow(InvalidVerificationTokenException::new).markVerified(now);
        } else {
            throw new InvalidVerificationTokenException();
        }
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