package io.github.gagann06.internshiptracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @Email alone accepts addresses with no top level domain, such as "name@gmail", because
// they are valid on a private network. The regexp also requires a dot and a TLD of two or
// more letters, so "name@gmail.com" and "name@outlook.co.uk" pass but "name@gmail" does not.
public record RegisterRequest(
        @NotBlank @Size(max = 254) @Email(regexp = ".+@.+\\.[A-Za-z]{2,}") String email,
        @NotBlank @Size(min = 8, max = 64) String password) {}
