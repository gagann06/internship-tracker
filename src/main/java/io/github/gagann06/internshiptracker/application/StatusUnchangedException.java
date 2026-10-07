package io.github.gagann06.internshiptracker.application;

public class StatusUnchangedException extends RuntimeException {

    public StatusUnchangedException(ApplicationStatus status) {
        super("The current status is already " + status);
    }
}