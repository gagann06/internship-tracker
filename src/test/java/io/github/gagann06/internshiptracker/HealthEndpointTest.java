package io.github.gagann06.internshiptracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Hosting platforms poll the health endpoint without credentials, so it must be public and say only UP or DOWN. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    MockMvcTester mvc;

    // Also proves the mail check is off: Mailpit isn't running during tests, so with it on
    // this would be DOWN. On a host, that would get the app restarted over a mail outage.
    @Test
    void healthIsPublicAndUpWithoutRevealingDetails() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.status").isEqualTo("UP");
                    assertThat(json).doesNotHavePath("$.components");
                });
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
