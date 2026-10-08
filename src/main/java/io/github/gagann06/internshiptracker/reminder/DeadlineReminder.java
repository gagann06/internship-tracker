package io.github.gagann06.internshiptracker.reminder;

import java.util.List;

public record DeadlineReminder(String email, List<ReminderItem> items) {}
