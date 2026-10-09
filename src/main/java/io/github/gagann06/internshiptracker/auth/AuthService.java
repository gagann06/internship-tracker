package io.github.gagann06.internshiptracker.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService, EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional 
    public User register(RegisterRequest request) {
        String trimmedEmail = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(trimmedEmail)) {
            throw new DuplicateEmailException(trimmedEmail);
        }

        User user = userRepository.save(new User(trimmedEmail, passwordEncoder.encode(request.password())));
        emailVerificationService.sendVerificationEmail(user);

        return user;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String trimmedEmail = normaliseEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(trimmedEmail).orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isVerified()) {
            throw new EmailNotVerifiedException();
        }

        return tokenService.issue(user);
    }

    private static String normaliseEmail(String email) {
        return email.strip().toLowerCase();
    }
}
