package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TokenServiceTest {

    @Autowired
    TokenService tokenService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SecretKey jwtSigningKey;

    @BeforeEach
    void cleanDatabase() {
        TestDatabase.clean(jdbcTemplate);
    }

    @Test
    void issuedTokenVerifiesWithTheSigningKeyAndCarriesTheExpectedClaims() {
        User user = userRepository.save(new User("gagan@example.com", "irrelevant-hash"));

        TokenResponse response = tokenService.issue(user);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);

        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("gagan@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("internship-tracker");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(jwt.getIssuedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void tokenIsRejectedByADecoderWithADifferentKey() {
        User user = userRepository.save(new User("gagan@example.com", "irrelevant-hash"));
        String token = tokenService.issue(user).accessToken();

        SecretKey otherKey = new SecretKeySpec(Base64.getDecoder().decode(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), "HmacSHA256");
        JwtDecoder attackerDecoder = NimbusJwtDecoder.withSecretKey(otherKey).macAlgorithm(MacAlgorithm.HS256).build();

        assertThatThrownBy(() -> attackerDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        User user = userRepository.save(new User("gagan@example.com", "irrelevant-hash"));
        String token = tokenService.issue(user).accessToken();

        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"999\",\"iss\":\"internship-tracker\"}".getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }
}
