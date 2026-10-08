package io.github.gagann06.internshiptracker.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;
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
 * Checks the reminder query's window edges, status filter and ordering against real Postgres.
 * "Today" is fixed so the test means the same thing whatever date it runs on.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApplicationRepositoryDueQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Company alicesCompany;
    Company bobsCompany;

    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        User alice = userRepository.save(new User("alice@example.com", "irrelevant-hash"));
        User bob = userRepository.save(new User("bob@example.com", "irrelevant-hash"));
        alicesCompany = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        bobsCompany = companyRepository.save(new Company(bob.getId(), "Citadel"));
    }

    @Test
    void returnsUnfinishedApplicationsDueFromTodayToThreeDaysAheadSoonestFirst() {
        save(alicesCompany, "due in three days (last day in window)", TODAY.plusDays(3), null);
        save(alicesCompany, "due today", TODAY, null);
        save(bobsCompany, "bob, due in two days", TODAY.plusDays(2), null);
        save(alicesCompany, "online assessment due tomorrow", TODAY.plusDays(1), ApplicationStatus.ONLINE_ASSESSMENT);

        save(alicesCompany, "due yesterday", TODAY.minusDays(1), null);
        save(alicesCompany, "due in four days", TODAY.plusDays(4), null);
        save(alicesCompany, "no deadline", null, null);
        save(alicesCompany, "rejected, due tomorrow", TODAY.plusDays(1), ApplicationStatus.REJECTED);
        save(alicesCompany, "withdrawn, due tomorrow", TODAY.plusDays(1), ApplicationStatus.WITHDRAWN);
        save(alicesCompany, "expired, due tomorrow", TODAY.plusDays(1), ApplicationStatus.EXPIRED);

        List<Application> due = applicationRepository.findDueBetween(TODAY, TODAY.plusDays(3), ApplicationStatus.FINISHED);

        assertThat(due).extracting(Application::getRoleTitle).containsExactly(
                "due today",
                "online assessment due tomorrow",
                "bob, due in two days",
                "due in three days (last day in window)");
    }

    @Test
    void companyIsLoadedWithTheApplicationSoItsNameIsReadableAfterTheQuery() {
        save(alicesCompany, "due today", TODAY, null);

        List<Application> due = applicationRepository.findDueBetween(TODAY, TODAY.plusDays(3), ApplicationStatus.FINISHED);

        // Outside any transaction here, so a lazy, unfetched company would throw LazyInitializationException.
        assertThat(due.get(0).getCompany().getName()).isEqualTo("Goldman Sachs");
    }

    private void save(Company company, String roleTitle, LocalDate deadline, ApplicationStatus status) {
        Application application = new Application(company, roleTitle);
        application.setDeadline(deadline);
        if (status != null) {
            application.changeStatus(status, null);
        }
        applicationRepository.save(application);
    }
}
