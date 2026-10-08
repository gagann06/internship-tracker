package io.github.gagann06.internshiptracker.application;

import io.github.gagann06.internshiptracker.company.Company;
import io.github.gagann06.internshiptracker.company.CompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final Long USER = 1000L;

    @Mock
    ApplicationRepository applicationRepository;

    @Mock
    CompanyRepository companyRepository;

    @Mock
    StatusChangeRepository statusChangeRepository;

    ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(applicationRepository, companyRepository, statusChangeRepository);
    }

    @Test
    void createThrowsWhenCompanyIsNotTheUsersAndSavesNothing() {
        when(companyRepository.findByIdAndOwnerId(999L, USER)).thenReturn(Optional.empty());
        ApplicationRequest request = new ApplicationRequest(999L, "SWE Intern", null, null, null);

        assertThatThrownBy(() -> applicationService.create(USER, request))
                .isInstanceOf(UnknownCompanyException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void createStripsRoleTitleCopiesOptionalFieldsAndInheritsTheOwner() {
        Company goldman = new Company(USER, "Goldman Sachs");
        when(companyRepository.findByIdAndOwnerId(1L, USER)).thenReturn(Optional.of(goldman));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationRequest request = new ApplicationRequest(
                1L,
                "  Summer Analyst  ",
                "Technology",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 15));

        Application created = applicationService.create(USER, request);

        assertThat(created.getCompany()).isSameAs(goldman);
        assertThat(created.getOwnerId()).isEqualTo(USER);
        assertThat(created.getRoleTitle()).isEqualTo("Summer Analyst");
        assertThat(created.getBusinessStream()).isEqualTo("Technology");
        assertThat(created.getAppliedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(created.getDeadline()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void getApplicationThrowsWhenNotFoundForThisUser() {
        when(applicationRepository.findByIdWithCompany(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplication(USER, 99L))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void updateThrowsWhenCompanyIsNotTheUsersAndLeavesApplicationUnchanged() {
        Company goldman = new Company(USER, "Goldman Sachs");
        Application existing = new Application(goldman, "Summer Analyst");
        when(applicationRepository.findByIdWithCompany(1L, USER)).thenReturn(Optional.of(existing));
        when(companyRepository.findByIdAndOwnerId(999L, USER)).thenReturn(Optional.empty());
        ApplicationRequest request = new ApplicationRequest(999L, "Changed", null, null, null);

        assertThatThrownBy(() -> applicationService.update(USER, 1L, request))
                .isInstanceOf(UnknownCompanyException.class);
        assertThat(existing.getCompany()).isSameAs(goldman);
        assertThat(existing.getRoleTitle()).isEqualTo("Summer Analyst");
    }

    @Test
    void updateReplacesEveryFieldIncludingClearingOmittedOnes() {
        Company goldman = new Company(USER, "Goldman Sachs");
        Company janeStreet = new Company(USER, "Jane Street");
        Application existing = new Application(goldman, "Summer Analyst");
        existing.setBusinessStream("Technology");
        existing.setDeadline(LocalDate.of(2026, 11, 15));
        when(applicationRepository.findByIdWithCompany(1L, USER)).thenReturn(Optional.of(existing));
        when(companyRepository.findByIdAndOwnerId(2L, USER)).thenReturn(Optional.of(janeStreet));
        ApplicationRequest request = new ApplicationRequest(2L, "  SWE Intern  ", null, null, null);

        Application updated = applicationService.update(USER, 1L, request);

        assertThat(updated.getCompany()).isSameAs(janeStreet);
        assertThat(updated.getRoleTitle()).isEqualTo("SWE Intern");
        assertThat(updated.getBusinessStream()).isNull();
        assertThat(updated.getDeadline()).isNull();
    }

    @Test
    void getHistoryThrowsWhenNotFoundForThisUserAndQueriesNothing() {
        when(applicationRepository.existsByIdAndOwnerId(99L, USER)).thenReturn(false);

        assertThatThrownBy(() -> applicationService.getHistory(USER, 99L))
                .isInstanceOf(ApplicationNotFoundException.class);
        verify(statusChangeRepository, never()).findByApplicationIdOrderByChangedAtAscIdAsc(any());
    }

    @Test
    void deleteThrowsWhenNotFoundForThisUserAndDeletesNothing() {
        when(applicationRepository.existsByIdAndOwnerId(99L, USER)).thenReturn(false);

        assertThatThrownBy(() -> applicationService.delete(USER, 99L))
                .isInstanceOf(ApplicationNotFoundException.class);
        verify(applicationRepository, never()).deleteById(any());
    }
}
