package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;

import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * End-to-end checks of the security filter chain using real tokens rather than
 * the {@code jwt()} test shortcut used by the other integration tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void requestWithoutATokenIsRejectedWith401AndABearerChallenge() {
        MvcTestResult result = mvc.get().uri("/api/companies").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void tokenFromARealLoginIsAccepted() throws Exception {
        String token = registerAndLogin("gagan@example.com", "correct-horse-battery");

        assertThat(mvc.get().uri("/api/companies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(mvc.get().uri("/api/companies").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithADifferentKeyIsRejected() {
        SecretKeySpec otherKey = new SecretKeySpec(
                Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), "HmacSHA256");
        JwtEncoder attackerEncoder = NimbusJwtEncoder.withSecretKey(otherKey).algorithm(MacAlgorithm.HS256).build();
        Instant now = Instant.now();
        String forged = sign(attackerEncoder, now, now.plus(1, ChronoUnit.HOURS));

        assertThat(mvc.get().uri("/api/companies").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        String expired = sign(jwtEncoder, twoHoursAgo, twoHoursAgo.plus(1, ChronoUnit.HOURS));

        assertThat(mvc.get().uri("/api/companies").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authEndpointsStayOpenWithoutAToken() {
        assertThat(mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@example.com", "password": "whatever"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
    }

    private String sign(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("internship-tracker")
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String registerAndLogin(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.CREATED);
        MvcTestResult login = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(login).hasStatusOk();
        return JsonPath.read(login.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }
}
