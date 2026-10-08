package io.github.gagann06.internshiptracker.company;

import io.github.gagann06.internshiptracker.application.ApplicationRepository;
import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
    ApplicationRepository applicationRepository;

    @BeforeEach
    void cleanDatabase() {
        applicationRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test 
    void createReturns201WithTheCompany() {
        assertThat(mvc.post().with(jwt()).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Goldman Sachs", "industry": "Investment Banking"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.name").isEqualTo("Goldman Sachs");
    }

    @Test
    void listReturnsEveryCompany() {
        companyRepository.save(new Company("Goldman Sachs", "Investment Banking"));
        companyRepository.save(new Company("Jane Street"));

        assertThat(mvc.get().with(jwt()).uri("/api/companies"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].name").asArray()
                .containsExactlyInAnyOrder("Goldman Sachs", "Jane Street");
    }

    @Test
    void getMissingCompanyReturns404ProblemDetail() {
        assertThat(mvc.get().with(jwt()).uri("/api/companies/{id}", 999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Company 999 not found");
    }

    @Test
    void createWithBlankNameReturns400() {
        assertThat(mvc.post().with(jwt()).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "   "}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDuplicateInDifferentCaseReturns409() {
        companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.post().with(jwt()).uri("/api/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "goldman sachs "}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(companyRepository.count()).isEqualTo(1);
    }

    @Test
    void updateCanChangeTheCaseOfItsOwnName() {
        Company saved = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.put().with(jwt()).uri("/api/companies/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Goldman sachs"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Goldman sachs");
    }

    @Test
    void updateToAnotherCompanysNameReturns409() {
        companyRepository.save(new Company("Goldman Sachs"));
        Company other = companyRepository.save(new Company("Jane Street"));

        assertThat(mvc.put().with(jwt()).uri("/api/companies/{id}", other.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "GOLDMAN SACHS"}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteReturns204AndTheCompanyIsGone() {
        Company saved = companyRepository.save(new Company("Goldman Sachs"));

        assertThat(mvc.delete().with(jwt()).uri("/api/companies/{id}", saved.getId()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().with(jwt()).uri("/api/companies/{id}", saved.getId()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void databaseRejectsCaseInsensitiveDuplicateEvenWithoutTheServiceCheck() {
        companyRepository.saveAndFlush(new Company("Goldman Sachs"));

        assertThatThrownBy(() -> companyRepository.saveAndFlush(new Company("goldman sachs")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
