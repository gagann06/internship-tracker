package io.github.gagann06.internshiptracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;

import io.github.gagann06.internshiptracker.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JwtPropertiesTest {

    @Autowired
    JwtProperties jwtProperties;

    @Test
    void secretIsResolvedAndLongEnoughForHs256() {
        assertThat(jwtProperties.secret()).doesNotContain("${");
        assertThat(Base64.getDecoder().decode(jwtProperties.secret())).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void expiryIsBoundAsADuration() {
        assertThat(jwtProperties.expiry()).isEqualTo(Duration.ofHours(1));
    }
}
