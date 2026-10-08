package io.github.gagann06.internshiptracker.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
 * Runs the real job against real Postgres, with time frozen and a notifier that records what it
 * was asked to send instead of emailing anyone.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeadlineReminderJobTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    /** 9am London on 10 November 2026 (GMT, so the same instant in UTC). */
    private static final Clock NOV_10_LONDON = Clock.fixed(Instant.parse("2026-11-10T09:00:00Z"), LONDON);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RecordingNotifier notifier;
    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        notifier = new RecordingNotifier();
        alice = userRepository.save(new User("alice@example.com", "irrelevant-hash"));
        bob = userRepository.save(new User("bob@example.com", "irrelevant-hash"));
    }

    @Test
    void sendsOneReminderPerUserListingTheirDueApplicationsSoonestFirst() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        Company janeStreet = companyRepository.save(new Company(alice.getId(), "Jane Street"));
        Company citadel = companyRepository.save(new Company(bob.getId(), "Citadel"));
        save(janeStreet, "SWE Intern", TODAY.plusDays(2));
        save(goldman, "Summer Analyst", TODAY);
        save(citadel, "Quant Intern", TODAY.plusDays(1));

        job(NOV_10_LONDON).sendDeadlineReminders();

        assertThat(notifier.sent).hasSize(2);
        DeadlineReminder toAlice = sentTo("alice@example.com");
        assertThat(toAlice.items()).containsExactly(
                new ReminderItem("Goldman Sachs", "Summer Analyst", ApplicationStatus.TO_APPLY, TODAY),
                new ReminderItem("Jane Street", "SWE Intern", ApplicationStatus.TO_APPLY, TODAY.plusDays(2)));
        assertThat(sentTo("bob@example.com").items()).extracting(ReminderItem::companyName)
                .containsExactly("Citadel");
    }

    @Test
    void userWithNothingDueReceivesNoReminder() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        Company citadel = companyRepository.save(new Company(bob.getId(), "Citadel"));
        save(goldman, "Summer Analyst", TODAY.plusDays(1));
        save(citadel, "Quant Intern", TODAY.plusDays(10));

        job(NOV_10_LONDON).sendDeadlineReminders();

        assertThat(notifier.sent).extracting(DeadlineReminder::email).containsExactly("alice@example.com");
    }

    @Test
    void sendsNothingWhenNothingIsDue() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        save(goldman, "Summer Analyst", null);

        job(NOV_10_LONDON).sendDeadlineReminders();

        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void todayIsWorkedOutInLondonNotUtc() {
        // 23:30 UTC on 30 June is already 00:30 on 1 July in London (BST, UTC+1).
        Clock justAfterMidnightInLondon = Clock.fixed(Instant.parse("2026-06-30T23:30:00Z"), LONDON);
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        save(goldman, "due 30 June (yesterday in London)", LocalDate.of(2026, 6, 30));
        save(goldman, "due 4 July (three days ahead in London)", LocalDate.of(2026, 7, 4));

        job(justAfterMidnightInLondon).sendDeadlineReminders();

        assertThat(sentTo("alice@example.com").items()).extracting(ReminderItem::roleTitle)
                .containsExactly("due 4 July (three days ahead in London)");
    }

    private DeadlineReminderJob job(Clock clock) {
        return new DeadlineReminderJob(applicationRepository, userRepository, notifier, clock);
    }

    private DeadlineReminder sentTo(String email) {
        return notifier.sent.stream().filter(reminder -> reminder.email().equals(email)).findFirst().orElseThrow();
    }

    private void save(Company company, String roleTitle, LocalDate deadline) {
        Application application = new Application(company, roleTitle);
        application.setDeadline(deadline);
        applicationRepository.save(application);
    }

    /** Stands in for email: keeps every reminder so the test can inspect it. */
    static class RecordingNotifier implements ReminderNotifier {
        final List<DeadlineReminder> sent = new ArrayList<>();

        @Override
        public void send(DeadlineReminder reminder) {
            sent.add(reminder);
        }
    }
}
