package io.github.gagann06.internshiptracker.stats;

import static io.github.gagann06.internshiptracker.application.ApplicationStatus.APPLIED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.ASSESSMENT_CENTRE;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.HIREVUE_COMPLETED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.OFFER;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.ONLINE_ASSESSMENT;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.ONLINE_ASSESSMENT_COMPLETED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.REJECTED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.WITHDRAWN;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StatsServiceFunnelTest {

    @Autowired
    StatsService statsService;

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
    Company alicesCompany;

    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        alice = userRepository.save(new User("alice@example.com", "irrelevant-hash"));
        bob = userRepository.save(new User("bob@example.com", "irrelevant-hash"));
        alicesCompany = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
    }

    @Test
    void countsEachApplicationOncePerStageItReached() {
        // Reached the OA, then rejected: still counts as reaching the OA stage.
        application(alicesCompany, APPLIED, ONLINE_ASSESSMENT, REJECTED);
        // Never logged as APPLIED, recorded straight as a completed HireVue: counts as Applied and HireVue.
        application(alicesCompany, HIREVUE_COMPLETED);
        // Both OA statuses: counted once for the OA stage. Goes all the way to an offer.
        application(alicesCompany, APPLIED, ONLINE_ASSESSMENT, ONLINE_ASSESSMENT_COMPLETED, ASSESSMENT_CENTRE, OFFER);
        // Never left TO_APPLY, and withdrawn before applying: neither counts anywhere.
        application(alicesCompany);
        application(alicesCompany, WITHDRAWN);
        // Bob's offer must not appear in Alice's funnel.
        application(companyRepository.save(new Company(bob.getId(), "Citadel")), APPLIED, OFFER);

        assertThat(statsService.funnel(alice.getId())).containsExactly(
                new FunnelStep(FunnelStage.APPLIED, 3, 100),
                new FunnelStep(FunnelStage.ONLINE_ASSESSMENT, 2, 67),
                new FunnelStep(FunnelStage.HIREVUE, 1, 33),
                new FunnelStep(FunnelStage.TELEPHONE_INTERVIEW, 0, 0),
                new FunnelStep(FunnelStage.VIDEO_INTERVIEW, 0, 0),
                new FunnelStep(FunnelStage.ASSESSMENT_CENTRE, 1, 33),
                new FunnelStep(FunnelStage.OFFER, 1, 33));
    }

    @Test
    void userWithNoApplicationsGetsEveryStageAtZeroWithoutDividingByZero() {
        assertThat(statsService.funnel(alice.getId()))
                .hasSize(FunnelStage.values().length)
                .allSatisfy(step -> {
                    assertThat(step.applications()).isZero();
                    assertThat(step.percentOfApplied()).isZero();
                });
    }

    private void application(Company company, ApplicationStatus... path) {
        Application application = new Application(company, "Role");
        for (ApplicationStatus status : path) {
            application.changeStatus(status, null);
        }
        applicationRepository.save(application);
    }
}
