package io.github.gagann06.internshiptracker.auth;

/** A one-time link that doesn't exist, has already been used or has expired. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("This link is invalid or has expired");
    }
}
