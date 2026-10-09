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
public class VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final Duration expiry;

    public VerificationEmailSender(JavaMailSender mailSender, @Value("${app.reminders.from}") String from, @Value("${app.verification.expiry}") Duration expiry) {
        this.mailSender = mailSender;
        this.from = from;
        this.expiry = expiry;
    }

    // Runs only after the transaction that created the token commits, so an email is never
    // sent for an account or token that was rolled back.
    @TransactionalEventListener
    public void send(VerificationEmailRequested event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Verify your email for Internship Tracker");
        message.setText("""
                Confirm this is your email address by opening the link below:

                %s

                The link expires in %d hours. If you did not create an account, ignore this email.
                """.formatted(event.link(), expiry.toHours()));

        // The account already exists by now, so a mail failure must not turn the request into
        // a 500. The user can ask for the email again. The link is not logged: it holds the token.
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Could not send verification email to {}", event.email(), ex);
        }
    }
}
