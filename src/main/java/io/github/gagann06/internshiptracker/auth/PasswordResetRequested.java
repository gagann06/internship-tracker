package io.github.gagann06.internshiptracker.auth;

public record PasswordResetRequested(String email, String link) {}
