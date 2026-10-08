package io.github.gagann06.internshiptracker.company;

import io.github.gagann06.internshiptracker.application.ApplicationRepository;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;

    public CompanyService(CompanyRepository companyRepository, ApplicationRepository applicationRepository) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional 
    public Company create(Long userId, String name, String industry) {
        String trimmedName = name.strip();

        if (companyRepository.existsByOwnerIdAndNameIgnoreCase(userId, trimmedName)) {
            throw new DuplicateCompanyNameException(trimmedName);
        }

        Company company = new Company(userId, trimmedName, industry);

        return companyRepository.save(company);
    }

    @Transactional(readOnly = true)
    public List<Company> listAll(Long userId) {
        return companyRepository.findAllByOwnerId(userId);
    }

    @Transactional(readOnly = true)
    public Company getCompany(Long userId, Long id) {
        return companyRepository.findByIdAndOwnerId(id, userId).orElseThrow(() -> new CompanyNotFoundException(id));
    }

    @Transactional
    public Company update(Long userId, Long id, String name, String industry) {
        Company company = getCompany(userId, id);
        String trimmedName = name.strip();

        if (companyRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(userId, trimmedName, id)) {
            throw new DuplicateCompanyNameException(trimmedName);
        }

        company.setName(trimmedName);
        company.setIndustry(industry);

        return company;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        if (!companyRepository.existsByIdAndOwnerId(id, userId)) {
            throw new CompanyNotFoundException(id);
        }

        if (applicationRepository.existsByCompanyId(id)) {
            throw new CompanyHasApplicationsException(id);
        }
        companyRepository.deleteById(id);
    }
}
