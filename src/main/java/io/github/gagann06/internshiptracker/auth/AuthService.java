package io.github.gagann06.internshiptracker.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional 
    public User register(RegisterRequest request) {
        String trimmedEmail = request.email().strip().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(trimmedEmail)) {
            throw new DuplicateEmailException(trimmedEmail);
        }

        User user = new User(trimmedEmail, passwordEncoder.encode(request.password()));

        return userRepository.save(user);
    }
}
