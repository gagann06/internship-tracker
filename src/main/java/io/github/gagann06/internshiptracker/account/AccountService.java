package io.github.gagann06.internshiptracker.account;

import io.github.gagann06.internshiptracker.application.ApplicationRepository;
import io.github.gagann06.internshiptracker.auth.InvalidCredentialsException;
import io.github.gagann06.internshiptracker.auth.User;
import io.github.gagann06.internshiptracker.auth.UserRepository;
import io.github.gagann06.internshiptracker.company.CompanyRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, CompanyRepository companyRepository, ApplicationRepository applicationRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = verifiedUser(userId, request.currentPassword());

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = verifiedUser(userId, request.password());
        applicationRepository.deleteAllOwnedBy(userId);
        companyRepository.deleteAllOwnedBy(userId);
        userRepository.delete(user);
    }

    private User verifiedUser(Long userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IncorrectPasswordException();
        }
        return user;
    }
}
