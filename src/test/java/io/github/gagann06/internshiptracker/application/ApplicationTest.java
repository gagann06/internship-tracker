package io.github.gagann06.internshiptracker.application;

import io.github.gagann06.internshiptracker.company.Company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApplicationTest {

    private final Company goldman = new Company("Goldman Sachs");

    @Test
    void newApplicationStartsAtToApplyWithOneHistoryEntry() {
        Application application = new Application(goldman, "Summer Analyst");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.TO_APPLY);
        assertThat(application.getStatusChanges()).hasSize(1);
        StatusChange first = application.getStatusChanges().get(0);
        assertThat(first.getFromStatus()).isNull();
        assertThat(first.getToStatus()).isEqualTo(ApplicationStatus.TO_APPLY);
    }

    @Test
    void changeStatusUpdatesStatusAndAppendsHistory() {
        Application application = new Application(goldman, "Summer Analyst");

        application.changeStatus(ApplicationStatus.APPLIED, "Submitted");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application.getStatusChanges()).hasSize(2);
        StatusChange latest = application.getStatusChanges().get(1);
        assertThat(latest.getFromStatus()).isEqualTo(ApplicationStatus.TO_APPLY);
        assertThat(latest.getToStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(latest.getNote()).isEqualTo("Submitted");
    }

    @Test
    void changeToCurrentStatusThrowsAndRecordsNothing() {
        Application application = new Application(goldman, "Summer Analyst");

        assertThatThrownBy(() -> application.changeStatus(ApplicationStatus.TO_APPLY, null))
                .isInstanceOf(StatusUnchangedException.class);
        assertThat(application.getStatusChanges()).hasSize(1);
    }

    @Test
    void historyCannotBeModifiedFromOutside() {
        Application application = new Application(goldman, "Summer Analyst");

        assertThatThrownBy(() -> application.getStatusChanges().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
