package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The frontend has to load before anyone has a token, so its files are public. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FrontendPagesTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void indexPageIsServedWithoutAToken() {
        assertThat(mvc.get().uri("/index.html"))
                .hasStatusOk()
                .bodyText().contains("<title>Internship Tracker</title>");
    }

    @Test
    void scriptAndStylesheetAreServedWithoutAToken() {
        assertThat(mvc.get().uri("/app.js")).hasStatusOk();
        assertThat(mvc.get().uri("/styles.css")).hasStatusOk();
    }
}
