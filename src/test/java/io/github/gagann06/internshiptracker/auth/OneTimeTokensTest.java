package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OneTimeTokensTest {

    // 32 random bytes in Base64 without padding is 43 characters.
    @Test
    void generatedTokenIs43UrlSafeCharacters() {
        String token = OneTimeTokens.generate();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void everyGeneratedTokenIsDifferent() {
        assertThat(OneTimeTokens.generate()).isNotEqualTo(OneTimeTokens.generate());
    }

    // The published SHA-256 of "abc", so this proves it is really SHA-256 and not just
    // some reversible encoding of the input.
    @Test
    void hashIsSha256AsLowercaseHex() {
        assertThat(OneTimeTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashingTheSameTokenTwiceGivesTheSameHash() {
        String token = OneTimeTokens.generate();

        assertThat(OneTimeTokens.hash(token)).isEqualTo(OneTimeTokens.hash(token)).hasSize(64);
    }

    @Test
    void differentTokensGiveDifferentHashes() {
        assertThat(OneTimeTokens.hash(OneTimeTokens.generate()))
                .isNotEqualTo(OneTimeTokens.hash(OneTimeTokens.generate()));
    }
}
