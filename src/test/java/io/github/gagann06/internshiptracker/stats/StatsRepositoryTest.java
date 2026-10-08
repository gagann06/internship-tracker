package io.github.gagann06.internshiptracker.stats;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;
import io.github.gagann06.internshiptracker.application.Application;
import io.github.gagann06.internshiptracker.application.ApplicationRepository;
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
class StatsRepositoryTest {

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
    void countsMyApplicationsPerCompanyMostFirstThenAlphabetical() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        Company janeStreet = companyRepository.save(new Company(alice.getId(), "Jane Street"));
        Company citadel = companyRepository.save(new Company(alice.getId(), "Citadel"));
        applications(goldman, 3);
        applications(janeStreet, 1);
        applications(citadel, 1);

        // Bob also has a "Goldman Sachs": a different company row that must not be merged into Alice's.
        Company bobsGoldman = companyRepository.save(new Company(bob.getId(), "Goldman Sachs"));
        applications(bobsGoldman, 5);

        assertThat(statsRepository.countApplicationsPerCompany(alice.getId())).containsExactly(
                new CompanyCount("Goldman Sachs", 3),
                new CompanyCount("Citadel", 1),
                new CompanyCount("Jane Street", 1));
    }

    @Test
    void companyWithNoApplicationsIsNotListed() {
        Company goldman = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        companyRepository.save(new Company(alice.getId(), "Unused Co"));
        applications(goldman, 2);

        assertThat(statsRepository.countApplicationsPerCompany(alice.getId()))
                .containsExactly(new CompanyCount("Goldman Sachs", 2));
    }

    @Test
    void userWithNoApplicationsGetsAnEmptyList() {
        assertThat(statsRepository.countApplicationsPerCompany(alice.getId())).isEmpty();
    }

    private void applications(Company company, int howMany) {
        for (int i = 1; i <= howMany; i++) {
            applicationRepository.save(new Application(company, "Role " + i));
        }
    }
}
