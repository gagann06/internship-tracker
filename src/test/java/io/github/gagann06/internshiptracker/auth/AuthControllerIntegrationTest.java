package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import com.jayway.jsonpath.JsonPath;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SecretKey jwtSigningKey;

    @BeforeEach
    void cleanDatabase() {
        TestDatabase.clean(jdbcTemplate);
    }

    @Test
    void registerReturns201WithLowercasedEmailAndNoPasswordHash() {
        assertThat(register("Gagan@Example.com", "correct-horse-battery"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.email").isEqualTo("gagan@example.com");
                    assertThat(json).doesNotHavePath("$.passwordHash");
                    assertThat(json).doesNotHavePath("$.password");
                });
    }

    @Test
    void registerStoresABcryptHashNotThePassword() {
        assertThat(register("gagan@example.com", "correct-horse-battery")).hasStatus(HttpStatus.CREATED);

        User stored = userRepository.findByEmailIgnoreCase("gagan@example.com").orElseThrow();
        assertThat(stored.getPasswordHash())
                .isNotEqualTo("correct-horse-battery")
                .startsWith("$2a$");
        assertThat(passwordEncoder.matches("correct-horse-battery", stored.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", stored.getPasswordHash())).isFalse();
    }

    @Test
    void registeringTheSameEmailInDifferentCaseReturns409() {
        assertThat(register("gagan@example.com", "correct-horse-battery")).hasStatus(HttpStatus.CREATED);

        assertThat(register("GAGAN@EXAMPLE.COM", "another-password"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-an-email", "gagan@gmail", "gagan@localhost", "gagan@gmail.c", "gagan@gmail.123",
            "gagan@@gmail.com", "@gmail.com", "gagan@.com", "gagan @gmail.com"})
    void invalidEmailReturns400(String email) {
        assertThat(register(email, "correct-horse-battery"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void validationErrorNamesEveryInvalidFieldWithoutEchoingValues() {
        assertThat(register("gagan@gmail", "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.detail").isEqualTo(
                            "email: must be a well-formed email address; password: size must be between 8 and 64");
                    assertThat(json).extractingPath("$.errors[*].field").asArray()
                            .containsExactly("email", "password");
                    assertThat(json).extractingPath("$.errors[1].message")
                            .isEqualTo("size must be between 8 and 64");
                    assertThat(json).doesNotHavePath("$.errors[0].rejectedValue");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"gagan@gmail.com", "gagan@outlook.co.uk", "u1234567@live.warwick.ac.uk", "first.last+tracker@example.org"})
    void validEmailReturns201(String email) {
        assertThat(register(email, "correct-horse-battery"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void passwordShorterThanEightCharactersReturns400() {
        assertThat(register("gagan@example.com", "short"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void loginWithCorrectPasswordReturnsABearerTokenForThatUser() throws Exception {
        assertThat(register("gagan@example.com", "correct-horse-battery")).hasStatus(HttpStatus.CREATED);
        Long userId = userRepository.findByEmailIgnoreCase("gagan@example.com").orElseThrow().getId();

        MvcTestResult result = login("gagan@example.com", "correct-horse-battery").exchange();

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.tokenType").isEqualTo("Bearer");
            assertThat(json).extractingPath("$.expiresIn").isEqualTo(3600);
        });
        String token = JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
        String subject = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(token).getSubject();
        assertThat(subject).isEqualTo(userId.toString());
    }

    @Test
    void loginEmailIsCaseInsensitive() {
        assertThat(register("gagan@example.com", "correct-horse-battery")).hasStatus(HttpStatus.CREATED);

        assertThat(login("GAGAN@Example.COM", "correct-horse-battery")).hasStatusOk();
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalResponses() {
        assertThat(register("gagan@example.com", "correct-horse-battery")).hasStatus(HttpStatus.CREATED);

        MvcTestResult wrongPassword = login("gagan@example.com", "wrong-password").exchange();
        MvcTestResult unknownEmail = login("nobody@example.com", "correct-horse-battery").exchange();

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
    }

    @Test
    void loginWithBlankPasswordReturns400() {
        assertThat(login("gagan@example.com", "")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }

    private MockMvcTester.MockMvcRequestBuilder register(String email, String password) {
        return mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }
}
