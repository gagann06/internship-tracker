package io.github.gagann06.internshiptracker.account;

public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException() {
        super("Password is incorrect");
    }
}