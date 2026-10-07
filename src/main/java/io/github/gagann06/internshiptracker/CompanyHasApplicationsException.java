package io.github.gagann06.internshiptracker;

public class CompanyHasApplicationsException extends RuntimeException {

    public CompanyHasApplicationsException(Long id) {
        super("Company " + id + " still has applications that need to be deleted first");
    }
}