package io.github.gagann06.internshiptracker.application;

import static io.github.gagann06.internshiptracker.TestAuth.as;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;
import io.github.gagann06.internshiptracker.auth.User;
import io.github.gagann06.internshiptracker.auth.UserRepository;
import io.github.gagann06.internshiptracker.company.Company;
import io.github.gagann06.internshiptracker.company.CompanyRepository;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Counts the SQL statements Hibernate sends, to keep the N+1 problem from coming back.
 * Listing applications must cost one query however many companies they belong to.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
class ApplicationQueryCountTest {

    private static final int COMPANIES = 50;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    TransactionTemplate transactionTemplate;

    User alice;
    Statistics statistics;

    @BeforeEach
    void setUp() {
        TestDatabase.clean(jdbcTemplate);
        alice = userRepository.save(new User("alice@example.com", "irrelevant-hash"));
        for (int i = 1; i <= COMPANIES; i++) {
            Company company = companyRepository.save(new Company(alice.getId(), "Company " + i));
            applicationRepository.save(new Application(company, "Summer Analyst"));
        }
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void listingApplicationsRunsOneQueryHoweverManyCompanies() {
        assertThat(mvc.get().with(as(alice)).uri("/api/applications"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(COMPANIES);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    // What the join fetch saves: loading the same list without it costs one query for the
    // applications, then one more per company as each is lazily loaded.
    @Test
    void withoutTheJoinFetchTheSameListCostsAnExtraQueryPerCompany() {
        transactionTemplate.executeWithoutResult(status ->
                applicationRepository.findAll().forEach(application -> application.getCompany().getName()));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + COMPANIES);
    }
}
