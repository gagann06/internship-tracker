package io.github.gagann06.internshiptracker.application;

public class UnknownCompanyException extends RuntimeException {

    public UnknownCompanyException(Long companyId) {
        super("companyId " + companyId + " doesn't refer to an existing company");
    }
}
