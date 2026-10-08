package io.github.gagann06.internshiptracker.reminder;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component 
public class EmailReminderNotifier implements ReminderNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailReminderNotifier(JavaMailSender mailSender, @Value("${app.reminders.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override 
    public void send(DeadlineReminder reminder) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(reminder.email());
        message.setSubject(subject(reminder.items().size()));
        message.setText(body(reminder.items()));
        mailSender.send(message);
    }

   private static String subject(int count) {
        return count == 1 ? "1 deadline in the next 3 days" : count + " deadlines in the next 3 days";
    }

    private static String body(List<ReminderItem> items) {
        StringBuilder body = new StringBuilder("These applications have deadlines coming up:\n\n");
        for (ReminderItem item : items) {
            body.append(DATE.format(item.deadline()))
                    .append("  ")
                    .append(item.companyName())
                    .append(" - ")
                    .append(item.roleTitle())
                    .append(" (").append(item.status()).append(")\n");
        }
        return body.toString();
    }
}