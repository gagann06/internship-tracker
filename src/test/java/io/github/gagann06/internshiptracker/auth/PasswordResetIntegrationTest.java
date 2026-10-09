package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The forgot password flow through HTTP, reading the emailed link from a mock mail sender. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final String EMAIL = "gagan@example.com";
    private static final String OLD_PASSWORD = "old-password-123";
    private static final String NEW_PASSWORD = "new-password-456";
    private static final String RESET_SUBJECT = "Reset your Internship Tracker password";
    private static final String VERIFY_SUBJECT = "Verify your email for Internship Tracker";
    private static final String RESET_PREFIX = "http://localhost:8080/#reset-password=";
    private static final String VERIFY_PREFIX = "http://localhost:8080/#verify-email=";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        assertThat(post("/api/auth/register", """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, OLD_PASSWORD))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void forgotPasswordEmailsAResetLinkThatExpiresInAnHour() {
        assertThat(forgotPassword(EMAIL)).hasStatus(HttpStatus.ACCEPTED);

        SimpleMailMessage email = lastEmail(RESET_SUBJECT);
        assertThat(email.getTo()).containsExactly(EMAIL);
        assertThat(email.getText()).contains(RESET_PREFIX).contains("expires in 1 hour.");
    }

    @Test
    void resettingWithTheLinkReplacesThePassword() {
        verifyAccount();
        forgotPassword(EMAIL).exchange();

        assertThat(resetPassword(token(RESET_SUBJECT, RESET_PREFIX), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(NEW_PASSWORD)).hasStatusOk();
        assertThat(login(OLD_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // The link was sent to the address, so opening it proves the user owns it.
    @Test
    void resettingAlsoVerifiesAnUnverifiedAccount() {
        forgotPassword(EMAIL).exchange();

        assertThat(resetPassword(token(RESET_SUBJECT, RESET_PREFIX), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(NEW_PASSWORD)).hasStatusOk();
    }

    @Test
    void forgotPasswordForAnUnknownEmailReturns202AndSendsNoResetEmail() {
        assertThat(forgotPassword("nobody@example.com")).hasStatus(HttpStatus.ACCEPTED);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(SimpleMailMessage::getSubject).doesNotContain(RESET_SUBJECT);
    }

    @Test
    void aResetLinkWorksOnlyOnce() {
        forgotPassword(EMAIL).exchange();
        String token = token(RESET_SUBJECT, RESET_PREFIX);

        assertThat(resetPassword(token, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(resetPassword(token, "another-password-789"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("This link is invalid or has expired");
        assertThat(login(NEW_PASSWORD)).hasStatusOk();
    }

    @Test
    void anExpiredResetLinkIsRejectedAndThePasswordStaysTheSame() {
        verifyAccount();
        forgotPassword(EMAIL).exchange();
        String token = token(RESET_SUBJECT, RESET_PREFIX);
        jdbcTemplate.update("UPDATE user_tokens SET expires_at = now() - interval '1 minute' WHERE purpose = 'PASSWORD_RESET'");

        assertThat(resetPassword(token, NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(login(OLD_PASSWORD)).hasStatusOk();
    }

    @Test
    void askingAgainMakesTheEarlierResetLinkStopWorking() {
        forgotPassword(EMAIL).exchange();
        String first = token(RESET_SUBJECT, RESET_PREFIX);
        forgotPassword(EMAIL).exchange();
        String second = token(RESET_SUBJECT, RESET_PREFIX);

        assertThat(resetPassword(first, NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resetPassword(second, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
    }

    // Tokens are looked up by hash and purpose, so a verification link can't reset a password.
    @Test
    void aVerificationLinkCannotResetAPassword() {
        String verificationToken = token(VERIFY_SUBJECT, VERIFY_PREFIX);

        assertThat(resetPassword(verificationToken, NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aResetLinkCannotVerifyAnEmail() {
        forgotPassword(EMAIL).exchange();

        assertThat(post("/api/auth/verify-email", """
                {"token": "%s"}
                """.formatted(token(RESET_SUBJECT, RESET_PREFIX)))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // Validation runs before the service, so a rejected password doesn't use up the link.
    @Test
    void aShortNewPasswordReturns400AndTheLinkStillWorks() {
        forgotPassword(EMAIL).exchange();
        String token = token(RESET_SUBJECT, RESET_PREFIX);

        assertThat(resetPassword(token, "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("newPassword");
        assertThat(resetPassword(token, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void aMailFailureStillReturns202() {
        doThrow(new MailSendException("SMTP server down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(forgotPassword(EMAIL)).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    void forgotPasswordWithAnInvalidEmailFailsValidation() {
        assertThat(forgotPassword("not-an-email")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(mailSender, never()).send(argThat((SimpleMailMessage message) -> RESET_SUBJECT.equals(message.getSubject())));
    }

    private void verifyAccount() {
        jdbcTemplate.update("UPDATE users SET email_verified_at = now() WHERE email = ?", EMAIL);
    }

    private SimpleMailMessage lastEmail(String subject) {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        return sent.getAllValues().stream()
                .filter(message -> subject.equals(message.getSubject()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No email with subject: " + subject));
    }

    private String token(String subject, String linkPrefix) {
        String text = lastEmail(subject).getText();
        int start = text.indexOf(linkPrefix) + linkPrefix.length();
        return text.substring(start, text.indexOf('\n', start)).strip();
    }

    private MockMvcTester.MockMvcRequestBuilder forgotPassword(String email) {
        return post("/api/auth/forgot-password", """
                {"email": "%s"}
                """.formatted(email));
    }

    private MockMvcTester.MockMvcRequestBuilder resetPassword(String token, String newPassword) {
        return post("/api/auth/reset-password", """
                {"token": "%s", "newPassword": "%s"}
                """.formatted(token, newPassword));
    }

    private MockMvcTester.MockMvcRequestBuilder login(String password) {
        return post("/api/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, password));
    }

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
