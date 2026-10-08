package io.github.gagann06.internshiptracker.reminder;

import java.time.LocalDate;
import io.github.gagann06.internshiptracker.application.ApplicationStatus;

public record ReminderItem(String companyName, String roleTitle, ApplicationStatus status, LocalDate deadline) {}
