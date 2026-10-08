package io.github.gagann06.internshiptracker.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import io.github.gagann06.internshiptracker.application.ApplicationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailReminderNotifierTest {

    @Mock
    JavaMailSender mailSender;

    @Captor
    ArgumentCaptor<SimpleMailMessage> sentMessage;

    @Test
    void sendsOneEmailListingEachDeadlineWithAFriendlyDate() {
        EmailReminderNotifier notifier = new EmailReminderNotifier(mailSender, "reminders@test.local");
        DeadlineReminder reminder = new DeadlineReminder("alice@example.com", List.of(
                new ReminderItem("Goldman Sachs", "Summer Analyst", ApplicationStatus.TO_APPLY, LocalDate.of(2026, 11, 10)),
                new ReminderItem("Jane Street", "SWE Intern", ApplicationStatus.ONLINE_ASSESSMENT, LocalDate.of(2026, 11, 12))));

        notifier.send(reminder);

        verify(mailSender).send(sentMessage.capture());
        SimpleMailMessage message = sentMessage.getValue();
        assertThat(message.getFrom()).isEqualTo("reminders@test.local");
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getSubject()).isEqualTo("2 deadlines in the next 3 days");
        assertThat(message.getText()).contains(
                "Tue 10 Nov  Goldman Sachs - Summer Analyst (TO_APPLY)",
                "Thu 12 Nov  Jane Street - SWE Intern (ONLINE_ASSESSMENT)");
    }

    @Test
    void subjectIsSingularForOneDeadline() {
        EmailReminderNotifier notifier = new EmailReminderNotifier(mailSender, "reminders@test.local");

        notifier.send(new DeadlineReminder("alice@example.com", List.of(
                new ReminderItem("Goldman Sachs", "Summer Analyst", ApplicationStatus.TO_APPLY, LocalDate.of(2026, 11, 10)))));

        verify(mailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().getSubject()).isEqualTo("1 deadline in the next 3 days");
    }
}
