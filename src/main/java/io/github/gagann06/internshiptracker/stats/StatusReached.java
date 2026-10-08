package io.github.gagann06.internshiptracker.stats;

import io.github.gagann06.internshiptracker.application.ApplicationStatus;

public record StatusReached(Long applicationId, ApplicationStatus status) {}
