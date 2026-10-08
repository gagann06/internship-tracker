package io.github.gagann06.internshiptracker.reminder;

public interface ReminderNotifier {
    void send(DeadlineReminder reminder);
}
