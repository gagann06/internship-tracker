package io.github.gagann06.internshiptracker.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String industry;
    private Instant createdAt;

    @Column(name = "user_id", nullable = false)
    private Long ownerId;

    protected Company() {
        // Required by JPA - Hibernate instantiates empty then populates fields
    }

    public Company(Long ownerId, String name) {
        this(ownerId, name, null);
    }

    public Company(Long ownerId, String name, String industry) {
        this.ownerId = ownerId;
        this.name = name;
        this.industry = industry;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }   

    public Long getOwnerId() {
        return ownerId;
    }
}
