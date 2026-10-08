package io.github.gagann06.internshiptracker.auth;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super(email + " is a duplicate");
    }
}
