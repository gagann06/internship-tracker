package io.github.gagann06.internshiptracker;

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

    @Mock
    ApplicationRepository applicationRepository;

    @Mock
    CompanyRepository companyRepository;

    ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(applicationRepository, companyRepository);
    }

    @Test
    void createThrowsWhenCompanyDoesNotExistAndSavesNothing() {
        when(companyRepository.findById(999L)).thenReturn(Optional.empty());
        ApplicationRequest request = new ApplicationRequest(999L, "SWE Intern", null, null, null);

        assertThatThrownBy(() -> applicationService.create(request))
                .isInstanceOf(UnknownCompanyException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void createStripsRoleTitleAndCopiesOptionalFields() {
        Company goldman = new Company("Goldman Sachs");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(goldman));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationRequest request = new ApplicationRequest(
                1L,
                "  Summer Analyst  ",
                "Technology",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 15));

        Application created = applicationService.create(request);

        assertThat(created.getCompany()).isSameAs(goldman);
        assertThat(created.getRoleTitle()).isEqualTo("Summer Analyst");
        assertThat(created.getBusinessStream()).isEqualTo("Technology");
        assertThat(created.getAppliedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(created.getDeadline()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void getApplicationThrowsWhenIdDoesNotExist() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplication(99L))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void updateThrowsWhenCompanyDoesNotExistAndLeavesApplicationUnchanged() {
        Company goldman = new Company("Goldman Sachs");
        Application existing = new Application(goldman, "Summer Analyst");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(companyRepository.findById(999L)).thenReturn(Optional.empty());
        ApplicationRequest request = new ApplicationRequest(999L, "Changed", null, null, null);

        assertThatThrownBy(() -> applicationService.update(1L, request))
                .isInstanceOf(UnknownCompanyException.class);
        assertThat(existing.getCompany()).isSameAs(goldman);
        assertThat(existing.getRoleTitle()).isEqualTo("Summer Analyst");
    }

    @Test
    void updateReplacesEveryFieldIncludingClearingOmittedOnes() {
        Company goldman = new Company("Goldman Sachs");
        Company janeStreet = new Company("Jane Street");
        Application existing = new Application(goldman, "Summer Analyst");
        existing.setBusinessStream("Technology");
        existing.setDeadline(LocalDate.of(2026, 11, 15));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(companyRepository.findById(2L)).thenReturn(Optional.of(janeStreet));
        ApplicationRequest request = new ApplicationRequest(2L, "  SWE Intern  ", null, null, null);

        Application updated = applicationService.update(1L, request);

        assertThat(updated.getCompany()).isSameAs(janeStreet);
        assertThat(updated.getRoleTitle()).isEqualTo("SWE Intern");
        assertThat(updated.getBusinessStream()).isNull();
        assertThat(updated.getDeadline()).isNull();
    }

    @Test
    void deleteThrowsWhenIdDoesNotExistAndDeletesNothing() {
        when(applicationRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> applicationService.delete(99L))
                .isInstanceOf(ApplicationNotFoundException.class);
        verify(applicationRepository, never()).deleteById(any());
    }
}
