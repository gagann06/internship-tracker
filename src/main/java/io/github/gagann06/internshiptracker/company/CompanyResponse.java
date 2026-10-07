package io.github.gagann06.internshiptracker.company;

import java.time.Instant;

public record CompanyResponse(Long id, String name, String industry, Instant createdAt) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getId(), company.getName(), company.getIndustry(), company.getCreatedAt());
    }
}