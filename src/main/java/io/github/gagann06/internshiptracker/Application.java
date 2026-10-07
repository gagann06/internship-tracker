package io.github.gagann06.internshiptracker;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "applications")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private String roleTitle;
    private String businessStream;
    private LocalDate appliedDate;
    private LocalDate deadline;
    private Instant createdAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.PERSIST)
    private List<StatusChange> statusChanges = new ArrayList<>();

    protected Application() {}

    public Application(Company company, String roleTitle) {
        this.company = company;
        this.roleTitle = roleTitle;
        this.createdAt = Instant.now();
        this.status = ApplicationStatus.TO_APPLY;
        statusChanges.add(new StatusChange(this, null, ApplicationStatus.TO_APPLY, null));
    }

    public void changeStatus(ApplicationStatus newStatus, String note) {
        if (this.status == newStatus) {
            throw new StatusUnchangedException(newStatus);
        }
        statusChanges.add(new StatusChange(this, this.status, newStatus, note));
        this.status = newStatus;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getBusinessStream() {
        return businessStream;
    }

    public void setBusinessStream(String businessStream) {
        this.businessStream = businessStream;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }   

    public ApplicationStatus getStatus() {
        return status;
    }

    public List<StatusChange> getStatusChanges() {
        return Collections.unmodifiableList(statusChanges);
    }
}
