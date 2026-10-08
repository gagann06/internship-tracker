package io.github.gagann06.internshiptracker.stats;

import static io.github.gagann06.internshiptracker.TestAuth.as;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.APPLIED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.ONLINE_ASSESSMENT;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;
import io.github.gagann06.internshiptracker.application.Application;
import io.github.gagann06.internshiptracker.application.ApplicationRepository;
import io.github.gagann06.internshiptracker.application.ApplicationStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StatsControllerIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        alice = userRepository.save(new User("alice@example.com", "irrelevant-hash"));
        bob = userRepository.save(new User("bob@example.com", "irrelevant-hash"));
    }

    @Test
    void returnsAllThreeSectionsForTheCallerOnly() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        Company janeStreet = companyRepository.save(new Company(alice.getId(), "Jane Street"));
        application(goldman, APPLIED, ONLINE_ASSESSMENT, REJECTED);
        application(goldman, APPLIED);
        application(janeStreet, APPLIED);
        Company bobsCompany = companyRepository.save(new Company(bob.getId(), "Citadel"));
        application(bobsCompany, APPLIED, ONLINE_ASSESSMENT);
        application(bobsCompany, APPLIED, ONLINE_ASSESSMENT);

        assertThat(mvc.get().with(as(alice)).uri("/api/stats"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.funnel[0].stage").isEqualTo("APPLIED");
                    assertThat(json).extractingPath("$.funnel[0].applications").isEqualTo(3);
                    assertThat(json).extractingPath("$.funnel[1].stage").isEqualTo("ONLINE_ASSESSMENT");
                    assertThat(json).extractingPath("$.funnel[1].applications").isEqualTo(1);
                    assertThat(json).extractingPath("$.funnel[1].percentOfApplied").isEqualTo(33);
                    assertThat(json).extractingPath("$.applicationsPerCompany[*].companyName").asArray()
                            .containsExactly("Goldman Sachs", "Jane Street");
                    // Pipeline order, not alphabetical: TO_APPLY -> APPLIED comes first.
                    assertThat(json).extractingPath("$.timeBetweenStatuses[*].fromStatus").asArray()
                            .containsExactly("TO_APPLY", "APPLIED", "ONLINE_ASSESSMENT");
                    assertThat(json).extractingPath("$.timeBetweenStatuses[0].transitions").isEqualTo(3);
                });
    }

    @Test
    void newUserGetsEmptyStatsNotAnError() {
        assertThat(mvc.get().with(as(alice)).uri("/api/stats"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.funnel.length()").isEqualTo(FunnelStage.values().length);
                    assertThat(json).extractingPath("$.funnel[0].applications").isEqualTo(0);
                    assertThat(json).extractingPath("$.timeBetweenStatuses").asArray().isEmpty();
                    assertThat(json).extractingPath("$.applicationsPerCompany").asArray().isEmpty();
                });
    }

    @Test
    void requiresAToken() {
        assertThat(mvc.get().uri("/api/stats")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private void application(Company company, ApplicationStatus... path) {
        Application application = new Application(company, "Role");
        for (ApplicationStatus status : path) {
            application.changeStatus(status, null);
        }
        applicationRepository.save(application);
    }
}
