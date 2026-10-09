package io.github.gagann06.internshiptracker.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class OneTimeTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimeTokens() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes); 
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String raw) {
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            String digestText = HexFormat.of().formatHex(hash);

            return digestText;

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}