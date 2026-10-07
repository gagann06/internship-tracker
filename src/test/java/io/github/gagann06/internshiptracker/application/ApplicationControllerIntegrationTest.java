package io.github.gagann06.internshiptracker.application;

import io.github.gagann06.internshiptracker.company.Company;
import io.github.gagann06.internshiptracker.company.CompanyRepository;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    JdbcTemplate jdbcTemplate;

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
    void createRecordsTheStartingStatusInTheHistory() {
        Company company = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": %d, "roleTitle": "SWE Intern"}
                        """.formatted(company.getId())))
                .hasStatus(HttpStatus.CREATED);

        Long applicationId = applicationRepository.findAll().get(0).getId();
        Long startingEntries = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes"
                        + " WHERE application_id = ? AND from_status IS NULL AND to_status = 'TO_APPLY'",
                Long.class, applicationId);
        assertThat(startingEntries).isEqualTo(1);
    }

    @Test
    void newApplicationStartsAtToApply() {
        Company company = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": %d, "roleTitle": "SWE Intern"}
                        """.formatted(company.getId())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.status").isEqualTo("TO_APPLY");
    }

    @Test
    void changeStatusReturnsNewStatusAndAppendsHistory() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.post().uri("/api/applications/{id}/status", application.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "APPLIED", "note": "Submitted via careers site"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("APPLIED");

        Long appendedEntries = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes WHERE application_id = ?"
                        + " AND from_status = 'TO_APPLY' AND to_status = 'APPLIED'"
                        + " AND note = 'Submitted via careers site'",
                Long.class, application.getId());
        Long totalEntries = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes WHERE application_id = ?",
                Long.class, application.getId());
        assertThat(appendedEntries).isEqualTo(1);
        assertThat(totalEntries).isEqualTo(2);
    }

    @Test
    void changeToTheCurrentStatusReturns409AndRecordsNothing() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.post().uri("/api/applications/{id}/status", application.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "TO_APPLY"}
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("The current status is already TO_APPLY");

        Long totalEntries = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes WHERE application_id = ?",
                Long.class, application.getId());
        assertThat(totalEntries).isEqualTo(1);
    }

    @Test
    void changeStatusToUnknownValueReturns400() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.post().uri("/api/applications/{id}/status", application.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "INTERVIEWING"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changeStatusWithoutStatusReturns400() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));

        assertThat(mvc.post().uri("/api/applications/{id}/status", application.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"note": "forgot the status"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changeStatusOfMissingApplicationReturns404() {
        assertThat(mvc.post().uri("/api/applications/{id}/status", 999)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "APPLIED"}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyReturnsEveryChangeOldestFirst() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));
        changeStatus(application.getId(), "APPLIED", "Submitted");
        changeStatus(application.getId(), "ONLINE_ASSESSMENT", null);

        assertThat(mvc.get().uri("/api/applications/{id}/status-changes", application.getId()))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(3);
                    assertThat(json).extractingPath("$[*].toStatus").asArray()
                            .containsExactly("TO_APPLY", "APPLIED", "ONLINE_ASSESSMENT");
                    assertThat(json).extractingPath("$[0].fromStatus").isNull();
                    assertThat(json).extractingPath("$[1].fromStatus").isEqualTo("TO_APPLY");
                    assertThat(json).extractingPath("$[1].note").isEqualTo("Submitted");
                });
    }

    @Test
    void historyOfMissingApplicationReturns404NotAnEmptyList() {
        assertThat(mvc.get().uri("/api/applications/{id}/status-changes", 999))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingAnApplicationRemovesItsHistory() {
        Company goldman = companyRepository.save(new Company("Goldman Sachs"));
        Application application = applicationRepository.save(new Application(goldman, "Summer Analyst"));
        changeStatus(application.getId(), "APPLIED", null);

        assertThat(mvc.delete().uri("/api/applications/{id}", application.getId()))
                .hasStatus(HttpStatus.NO_CONTENT);

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM status_changes WHERE application_id = ?",
                Long.class, application.getId());
        assertThat(remaining).isZero();
    }

    private void changeStatus(Long applicationId, String status, String note) {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        assertThat(mvc.post().uri("/api/applications/{id}/status", applicationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "note": %s}
                        """.formatted(status, noteJson)))
                .hasStatusOk();
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
