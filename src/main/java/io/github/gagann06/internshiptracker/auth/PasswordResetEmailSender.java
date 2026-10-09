package io.github.gagann06.internshiptracker.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final Duration expiry;

    public PasswordResetEmailSender(JavaMailSender mailSender,
                                    @Value("${app.reminders.from}") String from,
                                    @Value("${app.password-reset.expiry}") Duration expiry) {
        this.mailSender = mailSender;
        this.from = from;
        this.expiry = expiry;
    }

    // Same reasoning as VerificationEmailSender: only after commit, failures logged, the link never logged.
    @TransactionalEventListener
    public void send(PasswordResetRequested event) {
        long hours = expiry.toHours();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Reset your Internship Tracker password");
        message.setText("""
                Someone asked to reset the password for this account. To choose a new one, open the link below:

                %s

                The link expires in %s. If you did not ask for this, ignore this email: your password has not changed.
                """.formatted(event.link(), hours == 1 ? "1 hour" : hours + " hours"));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Could not send password reset email to {}", event.email(), ex);
        }
    }
}
