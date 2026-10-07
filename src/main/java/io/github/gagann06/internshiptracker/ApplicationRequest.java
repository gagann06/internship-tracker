package io.github.gagann06.internshiptracker;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApplicationRequest(@NotNull Long companyId, @NotBlank String roleTitle, String businessStream, LocalDate appliedDate, LocalDate deadline) {}
