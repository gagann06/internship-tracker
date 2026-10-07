package io.github.gagann06.internshiptracker.application;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Application " + id + " not found");
    }
}