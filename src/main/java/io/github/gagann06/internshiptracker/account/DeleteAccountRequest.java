package io.github.gagann06.internshiptracker.account;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank String password) {}