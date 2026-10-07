package io.github.gagann06.internshiptracker.company;

public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(Long id) {
        super("Company " + id + " not found");
    }
}