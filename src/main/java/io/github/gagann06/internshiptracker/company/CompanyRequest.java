package io.github.gagann06.internshiptracker.company;

import jakarta.validation.constraints.NotBlank;

public record CompanyRequest(@NotBlank String name, String industry) {}
