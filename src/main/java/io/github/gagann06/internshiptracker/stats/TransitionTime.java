package io.github.gagann06.internshiptracker.stats;

import java.math.BigDecimal;

import io.github.gagann06.internshiptracker.application.ApplicationStatus;

public interface TransitionTime {
    ApplicationStatus getFromStatus();
    ApplicationStatus getToStatus();
    BigDecimal getAverageDays();
    long getTransitions();
}
