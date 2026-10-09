package io.github.gagann06.internshiptracker.account;

import static io.github.gagann06.internshiptracker.TestAuth.as;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;
import io.github.gagann06.internshiptracker.application.Application;
import io.github.gagann06.internshiptracker.application.ApplicationRepository;
import io.github.gagann06.internshiptracker.auth.User;
import io.github.gagann06.internshiptracker.auth.UserRepository;
import io.github.gagann06.internshiptracker.company.Company;
import io.github.gagann06.internshiptracker.company.CompanyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    private static final String ALICE_PASSWORD = "alice-password";
    private static final String BOB_PASSWORD = "bob-password";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    User alice;
    User bob;

    // Real BCrypt hashes, unlike the other integration tests, because these endpoints
    // check the password and the tests log in afterwards.
    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        alice = userRepository.save(new User("alice@example.com", passwordEncoder.encode(ALICE_PASSWORD)));
        bob = userRepository.save(new User("bob@example.com", passwordEncoder.encode(BOB_PASSWORD)));
    }

    @Test
    void changePasswordReturns204AndOnlyTheNewPasswordLogsIn() {
        assertThat(changePassword(alice, ALICE_PASSWORD, "brand-new-password"))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login("alice@example.com", "brand-new-password")).hasStatusOk();
        assertThat(login("alice@example.com", ALICE_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePasswordWithWrongCurrentPasswordReturns403AndKeepsTheOldOne() {
        assertThat(changePassword(alice, "not-alices-password", "brand-new-password"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").isEqualTo("Password is incorrect");

        assertThat(login("alice@example.com", ALICE_PASSWORD)).hasStatusOk();
        assertThat(login("alice@example.com", "brand-new-password")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePasswordOnlyAffectsTheCaller() {
        assertThat(changePassword(alice, ALICE_PASSWORD, "brand-new-password"))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login("bob@example.com", BOB_PASSWORD)).hasStatusOk();
    }

    @Test
    void changePasswordToOneShorterThanEightCharactersReturns400() {
        assertThat(changePassword(alice, ALICE_PASSWORD, "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("newPassword");

        assertThat(login("alice@example.com", ALICE_PASSWORD)).hasStatusOk();
    }

    @Test
    void accountEndpointsRequireAToken() {
        assertThat(mvc.put().uri("/api/account/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "brand-new-password"}
                        """.formatted(ALICE_PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.delete().uri("/api/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(ALICE_PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteAccountRemovesTheUserWithTheirCompaniesApplicationsAndHistory() {
        Company alicesCompany = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        Application alicesApplication = applicationRepository.save(new Application(alicesCompany, "Summer Analyst"));

        assertThat(deleteAccount(alice, ALICE_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(userRepository.existsById(alice.getId())).isFalse();
        assertThat(companyRepository.findAllByOwnerId(alice.getId())).isEmpty();
        assertThat(applicationRepository.findAllWithCompany(alice.getId())).isEmpty();
        Long history = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes WHERE application_id = ?",
                Long.class, alicesApplication.getId());
        assertThat(history).isZero();
    }

    @Test
    void deleteAccountLeavesOtherUsersDataAlone() {
        Company alicesCompany = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        applicationRepository.save(new Application(alicesCompany, "Summer Analyst"));
        Company bobsCompany = companyRepository.save(new Company(bob.getId(), "Goldman Sachs"));
        Application bobsApplication = applicationRepository.save(new Application(bobsCompany, "Summer Analyst"));

        assertThat(deleteAccount(alice, ALICE_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(userRepository.existsById(bob.getId())).isTrue();
        assertThat(companyRepository.findAllByOwnerId(bob.getId())).hasSize(1);
        assertThat(applicationRepository.findAllWithCompany(bob.getId())).hasSize(1);
        Long history = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes WHERE application_id = ?",
                Long.class, bobsApplication.getId());
        assertThat(history).isEqualTo(1);
        assertThat(login("bob@example.com", BOB_PASSWORD)).hasStatusOk();
    }

    @Test
    void deleteAccountWithWrongPasswordReturns403AndDeletesNothing() {
        Company alicesCompany = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        applicationRepository.save(new Application(alicesCompany, "Summer Analyst"));

        assertThat(deleteAccount(alice, "not-alices-password"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").isEqualTo("Password is incorrect");

        assertThat(userRepository.existsById(alice.getId())).isTrue();
        assertThat(companyRepository.findAllByOwnerId(alice.getId())).hasSize(1);
        assertThat(applicationRepository.findAllWithCompany(alice.getId())).hasSize(1);
    }

    @Test
    void deleteAccountWithoutAPasswordReturns400() {
        assertThat(mvc.delete().with(as(alice)).uri("/api/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");

        assertThat(userRepository.existsById(alice.getId())).isTrue();
    }

    // Uses a real token from login, because the point is what happens to a genuine,
    // still unexpired token once the account behind it no longer exists.
    @Test
    void tokenOfADeletedAccountGets401FromTheAccountEndpoints() throws Exception {
        MvcTestResult login = login("alice@example.com", ALICE_PASSWORD).exchange();
        String token = JsonPath.read(login.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");

        assertThat(mvc.delete().uri("/api/account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(ALICE_PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.put().uri("/api/account/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "brand-new-password"}
                        """.formatted(ALICE_PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login("alice@example.com", ALICE_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MockMvcTester.MockMvcRequestBuilder changePassword(User user, String currentPassword, String newPassword) {
        return mvc.put().with(as(user)).uri("/api/account/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(currentPassword, newPassword));
    }

    private MockMvcTester.MockMvcRequestBuilder deleteAccount(User user, String password) {
        return mvc.delete().with(as(user)).uri("/api/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(password));
    }

    private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }
}
