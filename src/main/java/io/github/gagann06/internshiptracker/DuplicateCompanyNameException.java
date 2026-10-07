package io.github.gagann06.internshiptracker;

public class DuplicateCompanyNameException extends RuntimeException {

    public DuplicateCompanyNameException(String name) {
        super(name + " is a duplicate");
    }
}
