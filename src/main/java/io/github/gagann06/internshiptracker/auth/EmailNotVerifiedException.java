package io.github.gagann06.internshiptracker.auth;


public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Verify your email address before logging in");
    }
}