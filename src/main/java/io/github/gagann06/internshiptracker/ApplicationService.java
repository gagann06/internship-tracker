package io.github.gagann06.internshiptracker;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    
    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;

    public ApplicationService(ApplicationRepository applicationRepository, CompanyRepository companyRepository) {
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional 
    public Application create(ApplicationRequest request) {
        Long companyId = request.companyId();
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new UnknownCompanyException(companyId));
        
        Application application = new Application(company, request.roleTitle().strip());
        application.setBusinessStream(request.businessStream());
        application.setAppliedDate(request.appliedDate());
        application.setDeadline(request.deadline());
        
        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<Application> listAll() {
        return applicationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Application getApplication(Long id) {
        return applicationRepository.findById(id)
            .orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    @Transactional
    public Application update (Long id, ApplicationRequest request) {
        Application application = getApplication(id);
        Long companyId = request.companyId();
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new UnknownCompanyException(companyId));
        
        application.setCompany(company);
        application.setRoleTitle(request.roleTitle().strip());
        application.setBusinessStream(request.businessStream());
        application.setAppliedDate(request.appliedDate());
        application.setDeadline(request.deadline());

        return application;
    }

    @Transactional
    public void delete(Long id) {
        if (!applicationRepository.existsById(id)) {
            throw new ApplicationNotFoundException(id);
        }
        applicationRepository.deleteById(id);
    }
}
