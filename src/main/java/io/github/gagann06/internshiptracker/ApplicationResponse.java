package io.github.gagann06.internshiptracker;
import java.time.Instant;
import java.time.LocalDate;

public record ApplicationResponse(Long id, Long companyId, String companyName, String roleTitle, String businessStream, LocalDate appliedDate, LocalDate deadline, Instant createdAt) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(application.getId(),
                application.getCompany().getId(),
                application.getCompany().getName(),
                application.getRoleTitle(),
                application.getBusinessStream(),
                application.getAppliedDate(),
                application.getDeadline(),
                application.getCreatedAt());
    }
}
