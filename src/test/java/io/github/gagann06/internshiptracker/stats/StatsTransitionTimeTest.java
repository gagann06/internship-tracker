package io.github.gagann06.internshiptracker.stats;

import static io.github.gagann06.internshiptracker.application.ApplicationStatus.APPLIED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.ONLINE_ASSESSMENT;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.ONLINE_ASSESSMENT_COMPLETED;
import static io.github.gagann06.internshiptracker.application.ApplicationStatus.TO_APPLY;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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

/**
 * Status changes record "now" when created, so each test saves an application's history and then
 * pins every row to a known timestamp. That makes the expected averages exact.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StatsTransitionTimeTest {

    @Autowired
    StatsRepository statsRepository;

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
    void averagesTheTimeTakenForEachKindOfTransition() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        // APPLIED -> OA took 9 days here...
        Application first = history(goldman, APPLIED, ONLINE_ASSESSMENT, ONLINE_ASSESSMENT_COMPLETED);
        at(first, TO_APPLY, "2026-10-01T00:00:00Z");
        at(first, APPLIED, "2026-10-03T00:00:00Z");
        at(first, ONLINE_ASSESSMENT, "2026-10-12T00:00:00Z");
        at(first, ONLINE_ASSESSMENT_COMPLETED, "2026-10-14T00:00:00Z");
        // ...and 10.5 days here, so the APPLIED -> OA average is 9.75, rounded to 9.8.
        Application second = history(goldman, APPLIED, ONLINE_ASSESSMENT);
        at(second, TO_APPLY, "2026-10-01T00:00:00Z");
        at(second, APPLIED, "2026-10-02T00:00:00Z");
        at(second, ONLINE_ASSESSMENT, "2026-10-12T12:00:00Z");

        // Bob's very slow application must not affect Alice's averages.
        Application bobs = history(companyRepository.save(new Company(bob.getId(), "Citadel")), APPLIED);
        at(bobs, TO_APPLY, "2026-01-01T00:00:00Z");
        at(bobs, APPLIED, "2026-09-01T00:00:00Z");

        List<TransitionTime> times = statsRepository.averageTimeBetweenStatuses(alice.getId());

        assertThat(times).hasSize(3);
        assertTransition(times.get(0), APPLIED, ONLINE_ASSESSMENT, "9.8", 2);
        assertTransition(times.get(1), ONLINE_ASSESSMENT, ONLINE_ASSESSMENT_COMPLETED, "2.0", 1);
        assertTransition(times.get(2), TO_APPLY, APPLIED, "1.5", 2);
    }

    @Test
    void applicationThatNeverMovedContributesNothing() {
        history(companyRepository.save(new Company(alice.getId(), "Goldman Sachs")));

        assertThat(statsRepository.averageTimeBetweenStatuses(alice.getId())).isEmpty();
    }

    private void assertTransition(TransitionTime time, ApplicationStatus from, ApplicationStatus to,
                                  String averageDays, long transitions) {
        assertThat(time.getFromStatus()).isEqualTo(from);
        assertThat(time.getToStatus()).isEqualTo(to);
        assertThat(time.getAverageDays()).isEqualByComparingTo(averageDays);
        assertThat(time.getTransitions()).isEqualTo(transitions);
    }

    private Application history(Company company, ApplicationStatus... path) {
        Application application = new Application(company, "Role");
        for (ApplicationStatus status : path) {
            application.changeStatus(status, null);
        }
        return applicationRepository.save(application);
    }

    private void at(Application application, ApplicationStatus toStatus, String instant) {
        int updated = jdbcTemplate.update(
                "UPDATE status_changes SET changed_at = ? WHERE application_id = ? AND to_status = ?",
                Timestamp.from(Instant.parse(instant)), application.getId(), toStatus.name());
        assertThat(updated).as("history row for %s", toStatus).isEqualTo(1);
    }
}
