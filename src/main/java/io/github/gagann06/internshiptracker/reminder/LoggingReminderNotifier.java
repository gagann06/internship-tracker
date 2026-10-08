package io.github.gagann06.internshiptracker.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

@Component
public class LoggingReminderNotifier implements ReminderNotifier{
    
    private static final Logger log = LoggerFactory.getLogger(LoggingReminderNotifier.class);

    @Override
    public void send(DeadlineReminder reminder) {
        log.info("Deadline reminder for {}: {} item(s)", reminder.email(), reminder.items().size());
    }
}
