package io.github.gagann06.internshiptracker;

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

        when(companyRepository.existsByNameIgnoreCase("Goldman Sachs")).thenReturn(true);
        assertThatThrownBy(() -> companyService.create("Goldman Sachs", "Investment Banking")).isInstanceOf(DuplicateCompanyNameException.class);
        verify(companyRepository, never()).save(any());
    }

    @Test
    void createTrimsNameBeforeCheckingAndSaving() {
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company created = companyService.create("  Goldman Sachs  ", "Investment Banking");

        assertThat(created.getName()).isEqualTo("Goldman Sachs");
        verify(companyRepository).existsByNameIgnoreCase("Goldman Sachs");
    }

    @Test
    void getCompanyThrowsWhenIdDoesNotExist() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getCompany(99L)).isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void updateThrowsWhenAnotherCompanyHasTheName() {
        Company existing = new Company("Goldman Sachs", "Investment Banking");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(companyRepository.existsByNameIgnoreCaseAndIdNot("Morgan Stanley", 1L)).thenReturn(true);

        assertThatThrownBy(() -> companyService.update(1L, "Morgan Stanley", "Investment Banking")).isInstanceOf(DuplicateCompanyNameException.class);
        assertThat(existing.getName()).isEqualTo("Goldman Sachs");
    }

    @Test
    void deleteThrowsWhenIdDoesNotExistAndDeletesNothing() {
        when(companyRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> companyService.delete(99L)).isInstanceOf(CompanyNotFoundException.class);
        verify(companyRepository, never()).deleteById(any());
    }

    @Test
    void deleteThrowsWhenCompanyStillHasApplicationsAndDeletesNothing() {
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(applicationRepository.existsByCompanyId(1L)).thenReturn(true);

        assertThatThrownBy(() -> companyService.delete(1L)).isInstanceOf(CompanyHasApplicationsException.class);
        verify(companyRepository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesCompanyWithNoApplications() {
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(applicationRepository.existsByCompanyId(1L)).thenReturn(false);

        companyService.delete(1L);

        verify(companyRepository).deleteById(1L);
    }
}
