package io.github.gagann06.internshiptracker.application;

import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull ApplicationStatus status, String note) {}
