package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
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

    @Test
    void invalidEmailReturns400() {
        assertThat(register("not-an-email", "correct-horse-battery"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void passwordShorterThanEightCharactersReturns400() {
        assertThat(register("gagan@example.com", "short"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.count()).isZero();
    }

    private MockMvcTester.MockMvcRequestBuilder register(String email, String password) {
        return mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }
}
