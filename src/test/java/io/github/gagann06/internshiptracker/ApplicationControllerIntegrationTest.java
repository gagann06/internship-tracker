package io.github.gagann06.internshiptracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest 
@AutoConfigureMockMvc 
public class ApplicationControllerIntegrationTest {
    
    @Autowired
    MockMvcTester mvc;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    ApplicationRepository applicationRepository;

    @BeforeEach
    void cleanDatabase() {
        applicationRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test 
    void createReturns201WithTheCompany() {
        Company company = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": %d, "roleTitle": "SWE Intern"}
                        """.formatted(company.getId())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.companyName").isEqualTo("Goldman Sachs");
    }

    @Test
    void createTrimsRoleTitleAndStoresOptionalFields() {
        Company company = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": %d, "roleTitle": "  Summer Analyst  ", "businessStream": "Technology",
                         "appliedDate": "2026-10-01", "deadline": "2026-11-15"}
                        """.formatted(company.getId())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.roleTitle").isEqualTo("Summer Analyst");
                    assertThat(json).extractingPath("$.businessStream").isEqualTo("Technology");
                    assertThat(json).extractingPath("$.appliedDate").isEqualTo("2026-10-01");
                    assertThat(json).extractingPath("$.deadline").isEqualTo("2026-11-15");
                });
    }

    @Test
    void createWithUnknownCompanyReturns400AndSavesNothing() {
        assertThat(mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": 999, "roleTitle": "SWE Intern"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("companyId 999 doesn't refer to an existing company");
        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    void createWithoutRoleTitleReturns400() {
        Company company = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": %d}
                        """.formatted(company.getId())))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listReturnsApplicationsWithTheirCompanyNames() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Company janeStreet = companyRepository.save(new Company("Jane Street"));
        applicationRepository.save(new Application(goldman, "Summer Analyst"));
        applicationRepository.save(new Application(janeStreet, "SWE Intern"));

        assertThat(mvc.get().uri("/api/applications"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].companyName").asArray()
                .containsExactlyInAnyOrder("Goldman Sachs", "Jane Street");
    }

    @Test
    void getExistingApplicationReturns200WithItsCompanyName() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.get().uri("/api/applications/{id}", application.getId()))
                .hasStatusOk()
                .bodyJson().extractingPath("$.companyName").isEqualTo("Goldman Sachs");
    }

    @Test
    void getMissingApplicationReturns404() {
        assertThat(mvc.get().uri("/api/applications/{id}", 999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Application 999 not found");
    }

    @Test
    void updateMovesApplicationToAnotherCompanyAndReplacesEveryField() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Company janeStreet = companyRepository.save(new Company("Jane Street"));
        Application application = new Application(goldman, "Summer Analyst");
        application.setDeadline(LocalDate.of(2026, 11, 15));
        application = applicationRepository.save(application);

        assertThat(mvc.put().uri("/api/applications/{id}", application.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": %d, "roleTitle": "SWE Intern"}
                        """.formatted(janeStreet.getId())))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.companyName").isEqualTo("Jane Street");
                    assertThat(json).extractingPath("$.roleTitle").isEqualTo("SWE Intern");
                    assertThat(json).extractingPath("$.deadline").isNull();
                });
    }

    @Test
    void updateWithUnknownCompanyReturns400AndLeavesApplicationUnchanged() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.put().uri("/api/applications/{id}", application.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": 999, "roleTitle": "Changed"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(mvc.get().uri("/api/applications/{id}", application.getId()))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.companyName").isEqualTo("Goldman Sachs");
                    assertThat(json).extractingPath("$.roleTitle").isEqualTo("Summer Analyst");
                });
    }

    @Test
    void deleteReturns204AndTheApplicationIsGone() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.delete().uri("/api/applications/{id}", application.getId()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/applications/{id}", application.getId()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingACompanyThatHasApplicationsReturns409AndKeepsIt() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.delete().uri("/api/companies/{id}", goldman.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Company " + goldman.getId() + " still has applications that need to be deleted first");
        assertThat(companyRepository.existsById(goldman.getId())).isTrue();
    }
}
