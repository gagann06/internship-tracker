package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The API documentation must be readable without a token, while the API itself stays locked. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void apiDescriptionIsPublicAndAdvertisesBearerTokens() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.info.title").isEqualTo("Internship Application Tracker API");
                    assertThat(json).extractingPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
                    assertThat(json).extractingPath("$.components.securitySchemes.bearerAuth.bearerFormat").isEqualTo("JWT");
                    assertThat(json).extractingPath("$.paths['/api/applications/{id}/status']").isNotNull();
                });
    }

    @Test
    void swaggerUiPageIsPublic() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }

    @Test
    void openingTheDocsDidNotOpenTheApi() {
        assertThat(mvc.get().uri("/api/applications")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
