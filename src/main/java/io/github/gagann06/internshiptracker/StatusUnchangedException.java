package io.github.gagann06.internshiptracker;

public class StatusUnchangedException extends RuntimeException {

    public StatusUnchangedException(ApplicationStatus status) {
        super("The current status is already " + status);
    }
}