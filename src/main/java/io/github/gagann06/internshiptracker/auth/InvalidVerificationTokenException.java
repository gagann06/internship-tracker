package io.github.gagann06.internshiptracker.auth;

public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException() {
        super("This link is invalid or has expired");
    }
}
