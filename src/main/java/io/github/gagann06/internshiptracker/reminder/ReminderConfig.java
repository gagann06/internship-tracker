package io.github.gagann06.internshiptracker.reminder;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration 
@EnableScheduling
public class ReminderConfig {
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/London"));
    }
}
