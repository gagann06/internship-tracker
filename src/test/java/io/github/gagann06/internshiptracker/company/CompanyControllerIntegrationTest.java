package io.github.gagann06.internshiptracker.company;

import io.github.gagann06.internshiptracker.TestDatabase;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;
import io.github.gagann06.internshiptracker.auth.User;
import io.github.gagann06.internshiptracker.auth.UserRepository;

import static io.github.gagann06.internshiptracker.TestAuth.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CompanyControllerIntegrationTest {

    @Autowired
    MockMvcTester mvc;

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
    void createReturns201WithTheCompany() {
        assertThat(mvc.post().with(as(alice)).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Goldman Sachs", "industry": "Investment Banking"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.name").isEqualTo("Goldman Sachs");
        assertThat(companyRepository.findAll()).singleElement()
                .satisfies(company -> assertThat(company.getOwnerId()).isEqualTo(alice.getId()));
    }

    @Test
    void getOwnCompanyReturns200() {
        Company saved = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.get().with(as(alice)).uri("/api/companies/{id}", saved.getId()))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Goldman Sachs");
    }

    @Test
    void listReturnsOnlyMyCompanies() {
        companyRepository.save(new Company(alice.getId(), "Goldman Sachs", "Investment Banking"));
        companyRepository.save(new Company(alice.getId(), "Jane Street"));
        companyRepository.save(new Company(bob.getId(), "Citadel"));

        assertThat(mvc.get().with(as(alice)).uri("/api/companies"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].name").asArray()
                .containsExactlyInAnyOrder("Goldman Sachs", "Jane Street");
    }

    @Test
    void getMissingCompanyReturns404ProblemDetail() {
        assertThat(mvc.get().with(as(alice)).uri("/api/companies/{id}", 999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Company 999 not found");
    }

    @Test
    void createWithBlankNameReturns400() {
        assertThat(mvc.post().with(as(alice)).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "   "}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDuplicateInDifferentCaseReturns409() {
        companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.post().with(as(alice)).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "goldman sachs "}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(companyRepository.count()).isEqualTo(1);
    }

    @Test
    void twoUsersCanEachHaveACompanyWithTheSameName() {
        companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.post().with(as(bob)).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Goldman Sachs"}
                        """))
                .hasStatus(HttpStatus.CREATED);
        assertThat(companyRepository.count()).isEqualTo(2);
    }

    @Test
    void updateCanChangeTheCaseOfItsOwnName() {
        Company saved = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.put().with(as(alice)).uri("/api/companies/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Goldman sachs"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Goldman sachs");
    }

    @Test
    void updateToAnotherCompanysNameReturns409() {
        companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));
        Company other = companyRepository.save(new Company(alice.getId(), "Jane Street"));

        assertThat(mvc.put().with(as(alice)).uri("/api/companies/{id}", other.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "GOLDMAN SACHS"}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteReturns204AndTheCompanyIsGone() {
        Company saved = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.delete().with(as(alice)).uri("/api/companies/{id}", saved.getId()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().with(as(alice)).uri("/api/companies/{id}", saved.getId()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherUsersCompanyCannotBeReadAndLooksMissing() {
        Company alices = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.get().with(as(bob)).uri("/api/companies/{id}", alices.getId()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Company " + alices.getId() + " not found");
    }

    @Test
    void anotherUsersCompanyCannotBeUpdated() {
        Company alices = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.put().with(as(bob)).uri("/api/companies/{id}", alices.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Renamed By Bob"}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(companyRepository.findById(alices.getId()).orElseThrow().getName()).isEqualTo("Goldman Sachs");
    }

    @Test
    void anotherUsersCompanyCannotBeDeleted() {
        Company alices = companyRepository.save(new Company(alice.getId(), "Goldman Sachs"));

        assertThat(mvc.delete().with(as(bob)).uri("/api/companies/{id}", alices.getId()))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(companyRepository.existsById(alices.getId())).isTrue();
    }

    @Test
    void databaseRejectsCaseInsensitiveDuplicateForTheSameOwnerEvenWithoutTheServiceCheck() {
        companyRepository.saveAndFlush(new Company(alice.getId(), "Goldman Sachs"));

        assertThatThrownBy(() -> companyRepository.saveAndFlush(new Company(alice.getId(), "goldman sachs")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
