package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The whole verification flow through HTTP. The mail sender is a mock, so each test can read
 * the email that would have been sent and take the token out of its link.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationIntegrationTest {

    private static final String EMAIL = "gagan@example.com";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String LINK_PREFIX = "http://localhost:8080/#verify-email=";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        TestDatabase.clean(jdbcTemplate);
    }

    @Test
    void registerEmailsAVerificationLink() {
        register();

        SimpleMailMessage email = lastEmail();
        assertThat(email.getTo()).containsExactly(EMAIL);
        assertThat(email.getSubject()).isEqualTo("Verify your email for Internship Tracker");
        assertThat(email.getText()).contains(LINK_PREFIX).contains("expires in 24 hours");
    }

    @Test
    void loginBeforeVerifyingReturns403() {
        register();

        assertThat(login(PASSWORD))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").isEqualTo("Verify your email address before logging in");
    }

    // The password is checked first, so a wrong password on an unverified account looks exactly
    // like any other wrong password and doesn't reveal that the account exists.
    @Test
    void wrongPasswordOnAnUnverifiedAccountReturnsTheUsual401() {
        register();

        assertThat(login("wrong-password"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
    }

    @Test
    void verifyingWithTheEmailedTokenAllowsLogin() {
        register();

        assertThat(verifyEmail(tokenFrom(lastEmail()))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(PASSWORD)).hasStatusOk();
        assertThat(userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void theDatabaseStoresOnlyTheTokensHash() {
        register();
        String token = tokenFrom(lastEmail());

        String stored = jdbcTemplate.queryForObject("SELECT token_hash FROM user_tokens", String.class);
        assertThat(stored).isNotEqualTo(token).isEqualTo(OneTimeTokens.hash(token));
    }

    @Test
    void aLinkWorksOnlyOnce() {
        register();
        String token = tokenFrom(lastEmail());

        assertThat(verifyEmail(token)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(verifyEmail(token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("This link is invalid or has expired");
    }

    @Test
    void anExpiredLinkIsRejectedAndTheAccountStaysUnverified() {
        register();
        String token = tokenFrom(lastEmail());
        jdbcTemplate.update("UPDATE user_tokens SET expires_at = now() - interval '1 minute'");

        assertThat(verifyEmail(token)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(login(PASSWORD)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aMadeUpTokenIsRejected() {
        assertThat(verifyEmail("not-a-real-token")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aBlankTokenFailsValidation() {
        assertThat(verifyEmail(""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("token");
    }

    @Test
    void resendIssuesANewLinkAndTheOldOneStopsWorking() {
        register();
        String oldToken = tokenFrom(lastEmail());

        assertThat(resend(EMAIL)).hasStatus(HttpStatus.ACCEPTED);
        String newToken = tokenFrom(lastEmail());

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(verifyEmail(oldToken)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(verifyEmail(newToken)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void resendMatchesTheEmailIgnoringCase() {
        register();

        assertThat(resend("GAGAN@Example.COM")).hasStatus(HttpStatus.ACCEPTED);
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    // Same 202 whether or not the account exists, so the endpoint can't be used to find out.
    @Test
    void resendForAnUnknownEmailReturns202AndSendsNothing() {
        assertThat(resend("nobody@example.com")).hasStatus(HttpStatus.ACCEPTED);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendForAVerifiedAccountReturns202AndSendsNothing() {
        register();
        verifyEmail(tokenFrom(lastEmail())).exchange();

        assertThat(resend(EMAIL)).hasStatus(HttpStatus.ACCEPTED);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void aMailFailureDoesNotFailRegistration() {
        doThrow(new MailSendException("SMTP server down")).when(mailSender).send(any(SimpleMailMessage.class));

        register();

        assertThat(userRepository.findByEmailIgnoreCase(EMAIL)).isPresent();
    }

    // user_tokens rows reference the user, so without ON DELETE CASCADE this delete would fail.
    @Test
    void anAccountWithTokensCanStillBeDeleted() throws Exception {
        register();
        verifyEmail(tokenFrom(lastEmail())).exchange();
        resend(EMAIL).exchange();
        String accessToken = JsonPath.read(
                login(PASSWORD).exchange().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");

        assertThat(mvc.delete().uri("/api/account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_tokens", Long.class)).isZero();
    }

    private void register() {
        assertThat(mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, PASSWORD)))
                .hasStatus(HttpStatus.CREATED);
    }

    private SimpleMailMessage lastEmail() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        List<SimpleMailMessage> all = sent.getAllValues();
        return all.get(all.size() - 1);
    }

    private static String tokenFrom(SimpleMailMessage email) {
        String text = email.getText();
        int start = text.indexOf(LINK_PREFIX) + LINK_PREFIX.length();
        int end = text.indexOf('\n', start);
        return text.substring(start, end).strip();
    }

    private MockMvcTester.MockMvcRequestBuilder login(String password) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, password));
    }

    private MockMvcTester.MockMvcRequestBuilder verifyEmail(String token) {
        return mvc.post().uri("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}
                        """.formatted(token));
    }

    private MockMvcTester.MockMvcRequestBuilder resend(String email) {
        return mvc.post().uri("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email));
    }
}
