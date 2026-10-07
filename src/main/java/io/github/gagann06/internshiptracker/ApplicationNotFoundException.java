package io.github.gagann06.internshiptracker;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Application " + id + " not found");
    }
}