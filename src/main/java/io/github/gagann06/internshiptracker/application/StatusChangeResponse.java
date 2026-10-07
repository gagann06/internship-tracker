package io.github.gagann06.internshiptracker.application;

import java.time.Instant;

public record StatusChangeResponse(ApplicationStatus fromStatus, ApplicationStatus toStatus, Instant changedAt, String note) {
    public static StatusChangeResponse from(StatusChange status) {
        return new StatusChangeResponse(status.getFromStatus(), status.getToStatus(), status.getChangedAt(), status.getNote());
    }
}
