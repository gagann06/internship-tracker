package io.github.gagann06.internshiptracker.reminder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.gagann06.internshiptracker.application.Application;
import io.github.gagann06.internshiptracker.application.ApplicationRepository;
import io.github.gagann06.internshiptracker.application.ApplicationStatus;
import io.github.gagann06.internshiptracker.auth.User;
import io.github.gagann06.internshiptracker.auth.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeadlineReminderJob {
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ReminderNotifier reminderNotifier;
    private final Clock clock;

    public DeadlineReminderJob(ApplicationRepository applicationRepository, UserRepository userRepository, ReminderNotifier reminderNotifier, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.reminderNotifier = reminderNotifier;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/London")
    @Transactional(readOnly = true)
    public void sendDeadlineReminders() {
        LocalDate today = LocalDate.now(clock);
        List<Application> due = applicationRepository.findDueBetween(today, today.plusDays(3), ApplicationStatus.FINISHED);

        Map<Long, List<Application>> byOwner = due.stream()
                .collect(Collectors.groupingBy(Application::getOwnerId));

        Map<Long, String> emailById = new HashMap<>();
        for (User user : userRepository.findAllById(byOwner.keySet())) {
            emailById.put(user.getId(), user.getEmail());
        }

        byOwner.forEach((ownerId, applications) -> {
            List<ReminderItem> items = applications.stream()
                    .map(a -> new ReminderItem(a.getCompany().getName(), a.getRoleTitle(), a.getStatus(), a.getDeadline()))
                    .toList();
            reminderNotifier.send(new DeadlineReminder(emailById.get(ownerId), items));
        });
    }
}
