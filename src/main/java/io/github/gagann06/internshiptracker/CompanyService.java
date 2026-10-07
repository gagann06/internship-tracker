package io.github.gagann06.internshiptracker;

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
    public Company create(String name, String industry) {
        String trimmedName = name.strip();

        if (companyRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateCompanyNameException(trimmedName);
        }

        Company company = new Company(trimmedName, industry);

        return companyRepository.save(company);
    }

    @Transactional(readOnly = true)
    public List<Company> listAll() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Company getCompany(Long id) {
        return companyRepository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }

    @Transactional
    public Company update(Long id, String name, String industry) {
        Company company = getCompany(id);
        String trimmedName = name.strip();

        if (companyRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new DuplicateCompanyNameException(trimmedName);
        }

        company.setName(trimmedName);
        company.setIndustry(industry);

        return company;
    }

    @Transactional
    public void delete(Long id) {
        if (!companyRepository.existsById(id)) {
            throw new CompanyNotFoundException(id);
        }

        if (applicationRepository.existsByCompanyId(id)) {
            throw new CompanyHasApplicationsException(id);
        }
        companyRepository.deleteById(id);
    }
}
