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
    public Application create(Long userId, ApplicationRequest request) {
        Company company = findOwnedCompany(userId, request.companyId());

        Application application = new Application(company, request.roleTitle().strip());
        application.setBusinessStream(request.businessStream());
        application.setAppliedDate(request.appliedDate());
        application.setDeadline(request.deadline());

        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<Application> listAll(Long userId) {
        return applicationRepository.findAllWithCompany(userId);
    }

    @Transactional(readOnly = true)
    public Application getApplication(Long userId, Long id) {
        return applicationRepository.findByIdWithCompany(id, userId)
            .orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    @Transactional
    public Application update(Long userId, Long id, ApplicationRequest request) {
        Application application = getApplication(userId, id);
        Company company = findOwnedCompany(userId, request.companyId());

        application.setCompany(company);
        application.setRoleTitle(request.roleTitle().strip());
        application.setBusinessStream(request.businessStream());
        application.setAppliedDate(request.appliedDate());
        application.setDeadline(request.deadline());

        return application;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOwnedApplication(userId, id);
        applicationRepository.deleteById(id);
    }

    @Transactional
    public Application changeStatus(Long userId, Long id, ApplicationStatus newStatus, String note) {
        Application application = getApplication(userId, id);
        application.changeStatus(newStatus, note);

        return application;
    }

    @Transactional(readOnly = true)
    public List<StatusChange> getHistory(Long userId, Long id) {
        requireOwnedApplication(userId, id);
        return statusChangeRepository.findByApplicationIdOrderByChangedAtAscIdAsc(id);
    }

    // Another user's company is reported exactly like a missing one, so its existence isn't revealed.
    private Company findOwnedCompany(Long userId, Long companyId) {
        return companyRepository.findByIdAndOwnerId(companyId, userId)
            .orElseThrow(() -> new UnknownCompanyException(companyId));
    }

    private void requireOwnedApplication(Long userId, Long id) {
        if (!applicationRepository.existsByIdAndOwnerId(id, userId)) {
            throw new ApplicationNotFoundException(id);
        }
    }
}
