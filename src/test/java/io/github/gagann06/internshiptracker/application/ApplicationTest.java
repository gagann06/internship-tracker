package io.github.gagann06.internshiptracker.application;

import io.github.gagann06.internshiptracker.company.Company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final Long ALICE = 1000L;
    private static final Long BOB = 1001L;

    private final Company goldman = new Company(ALICE, "Goldman Sachs");

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
    void applicationTakesItsOwnerFromItsCompany() {
        Application application = new Application(goldman, "Summer Analyst");

        assertThat(application.getOwnerId()).isEqualTo(ALICE);
    }

    @Test
    void applicationCanMoveToAnotherCompanyOfTheSameOwner() {
        Application application = new Application(goldman, "Summer Analyst");
        Company janeStreet = new Company(ALICE, "Jane Street");

        application.setCompany(janeStreet);

        assertThat(application.getCompany()).isSameAs(janeStreet);
    }

    @Test
    void applicationCannotMoveToAnotherUsersCompany() {
        Application application = new Application(goldman, "Summer Analyst");
        Company bobsCompany = new Company(BOB, "Citadel");

        assertThatThrownBy(() -> application.setCompany(bobsCompany))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(application.getCompany()).isSameAs(goldman);
    }

    @Test
    void ownershipCheckComparesValuesNotObjectIdentity() {
        // Long values above 127 are not cached, so two equal ids are distinct objects.
        // An == comparison would wrongly reject this move; equals() accepts it.
        Application application = new Application(new Company(Long.valueOf(5000), "Goldman Sachs"), "Summer Analyst");
        Company sameOwnerDifferentLongObject = new Company(Long.valueOf(5000), "Jane Street");

        application.setCompany(sameOwnerDifferentLongObject);

        assertThat(application.getCompany()).isSameAs(sameOwnerDifferentLongObject);
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
