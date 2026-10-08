package io.github.gagann06.internshiptracker.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public TokenResponse issue(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("internship-tracker")
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiry()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
        
        return new TokenResponse(token, "Bearer", jwtProperties.expiry().toSeconds());
    }
}
