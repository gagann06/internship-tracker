package io.github.gagann06.internshiptracker.application;

import io.github.gagann06.internshiptracker.company.Company;
import io.github.gagann06.internshiptracker.company.CompanyRepository;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    
    private final StatusChangeRepository statusChangeRepository;
    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;

    public ApplicationService(ApplicationRepository applicationRepository, CompanyRepository companyRepository, StatusChangeRepository statusChangeRepository) {
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.statusChangeRepository = statusChangeRepository;
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
        return applicationRepository.findAllWithCompany();
    }

    @Transactional(readOnly = true)
    public Application getApplication(Long id) {
        return applicationRepository.findByIdWithCompany(id)
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

    @Transactional 
    public Application changeStatus(Long id, ApplicationStatus newStatus, String note) {
        Application application = getApplication(id);
        application.changeStatus(newStatus, note);

        return application;
    }

    @Transactional(readOnly = true)
    public List<StatusChange> getHistory(Long id) {
        if (!applicationRepository.existsById(id)) {
            throw new ApplicationNotFoundException(id);
        }
        return statusChangeRepository.findByApplicationIdOrderByChangedAtAscIdAsc(id);
    }
}
