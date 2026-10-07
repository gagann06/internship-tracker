package io.github.gagann06.internshiptracker;

public class UnknownCompanyException extends RuntimeException {

    public UnknownCompanyException(Long companyId) {
        super("companyId " + companyId + " doesn't refer to an existing company");
    }
}
