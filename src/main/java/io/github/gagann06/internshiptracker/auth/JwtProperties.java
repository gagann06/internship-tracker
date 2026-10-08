package io.github.gagann06.internshiptracker.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code app.jwt.*} in application.yaml.
 *
 * @param secret Base64-encoded HMAC key, at least 32 bytes once decoded
 * @param expiry how long an issued access token stays valid
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiry) {}
