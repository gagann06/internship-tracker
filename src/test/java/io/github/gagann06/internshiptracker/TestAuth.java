package io.github.gagann06.internshiptracker;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.github.gagann06.internshiptracker.auth.User;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Authenticates a test request as a saved user, as if they had sent a valid token whose
 * {@code sub} claim is their id. Real token handling is covered by SecurityIntegrationTest.
 */
public final class TestAuth {

    private TestAuth() {}

    public static JwtRequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }
}
