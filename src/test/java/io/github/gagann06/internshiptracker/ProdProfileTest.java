package io.github.gagann06.internshiptracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The prod profile starts with its required mail settings supplied and hides the API documentation. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "MAIL_HOST=smtp.example.com",
        "MAIL_USERNAME=user",
        "MAIL_PASSWORD=password",
        "MAIL_FROM=reminders@example.com"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProdProfileTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void apiDocumentationIsSwitchedOff() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthStillWorks() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void theApiStillNeedsAToken() {
        assertThat(mvc.get().uri("/api/applications")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
