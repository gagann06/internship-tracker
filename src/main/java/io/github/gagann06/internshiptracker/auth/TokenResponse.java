package io.github.gagann06.internshiptracker.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
