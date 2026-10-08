package io.github.gagann06.internshiptracker.company;

import io.github.gagann06.internshiptracker.application.ApplicationRepository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;



@ExtendWith(MockitoExtension.class)
public class CompanyServiceTest {

    private static final Long USER = 1000L;

    @Mock
    CompanyRepository companyRepository;

    @Mock
    ApplicationRepository applicationRepository;

    CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository, applicationRepository);
    }

    @Test
    void createThrowsExceptionWhenCompanyExists() {

        when(companyRepository.existsByOwnerIdAndNameIgnoreCase(USER, "Goldman Sachs")).thenReturn(true);
        assertThatThrownBy(() -> companyService.create(USER, "Goldman Sachs", "Investment Banking")).isInstanceOf(DuplicateCompanyNameException.class);
        verify(companyRepository, never()).save(any());
    }

    @Test
    void createTrimsNameAndRecordsTheOwner() {
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company created = companyService.create(USER, "  Goldman Sachs  ", "Investment Banking");

        assertThat(created.getName()).isEqualTo("Goldman Sachs");
        assertThat(created.getOwnerId()).isEqualTo(USER);
        verify(companyRepository).existsByOwnerIdAndNameIgnoreCase(USER, "Goldman Sachs");
    }

    @Test
    void getCompanyThrowsWhenNotFoundForThisUser() {
        when(companyRepository.findByIdAndOwnerId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getCompany(USER, 99L)).isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void updateThrowsWhenAnotherCompanyHasTheName() {
        Company existing = new Company(USER, "Goldman Sachs", "Investment Banking");
        when(companyRepository.findByIdAndOwnerId(1L, USER)).thenReturn(Optional.of(existing));
        when(companyRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(USER, "Morgan Stanley", 1L)).thenReturn(true);

        assertThatThrownBy(() -> companyService.update(USER, 1L, "Morgan Stanley", "Investment Banking")).isInstanceOf(DuplicateCompanyNameException.class);
        assertThat(existing.getName()).isEqualTo("Goldman Sachs");
    }

    @Test
    void deleteThrowsWhenNotFoundForThisUserAndDeletesNothing() {
        when(companyRepository.existsByIdAndOwnerId(99L, USER)).thenReturn(false);

        assertThatThrownBy(() -> companyService.delete(USER, 99L)).isInstanceOf(CompanyNotFoundException.class);
        verify(companyRepository, never()).deleteById(any());
    }

    @Test
    void deleteThrowsWhenCompanyStillHasApplicationsAndDeletesNothing() {
        when(companyRepository.existsByIdAndOwnerId(1L, USER)).thenReturn(true);
        when(applicationRepository.existsByCompanyId(1L)).thenReturn(true);

        assertThatThrownBy(() -> companyService.delete(USER, 1L)).isInstanceOf(CompanyHasApplicationsException.class);
        verify(companyRepository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesCompanyWithNoApplications() {
        when(companyRepository.existsByIdAndOwnerId(1L, USER)).thenReturn(true);
        when(applicationRepository.existsByCompanyId(1L)).thenReturn(false);

        companyService.delete(USER, 1L);

        verify(companyRepository).deleteById(1L);
    }
}
