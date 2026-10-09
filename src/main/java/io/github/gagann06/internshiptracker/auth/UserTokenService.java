package io.github.gagann06.internshiptracker.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems the one-time tokens behind emailed links. Only a token's hash is stored;
 * the raw value exists only in memory and in the email.
 */
@Service
public class UserTokenService {

    private final UserTokenRepository userTokenRepository;
    private final Clock clock;

    public UserTokenService(UserTokenRepository userTokenRepository, Clock clock) {
        this.userTokenRepository = userTokenRepository;
        this.clock = clock;
    }

    /**
     * Creates a token and returns its raw value for the link. Any unused token of the same
     * purpose is deleted first, so only the most recently emailed link works.
     */
    @Transactional
    public String issue(Long userId, UserTokenPurpose purpose, Duration expiry) {
        Instant now = Instant.now(clock);
        userTokenRepository.deleteUnusedTokensForUserAndPurpose(userId, purpose);
        String rawToken = OneTimeTokens.generate();
        userTokenRepository.save(new UserToken(userId, purpose, OneTimeTokens.hash(rawToken), now.plus(expiry)));
        return rawToken;
    }

    /** Marks the token used and returns it, or throws if it is unknown, used or expired. */
    @Transactional
    public UserToken consume(String rawToken, UserTokenPurpose purpose) {
        Instant now = Instant.now(clock);
        UserToken token = userTokenRepository.findByTokenHashAndPurpose(OneTimeTokens.hash(rawToken), purpose)
                .filter(found -> found.isUsable(now))
                .orElseThrow(InvalidTokenException::new);
        token.markUsed(now);
        return token;
    }
}
