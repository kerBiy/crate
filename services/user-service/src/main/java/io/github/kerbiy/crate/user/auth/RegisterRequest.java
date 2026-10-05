package io.github.kerbiy.crate.user.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        // Case-insensitive on input, stored lowercase.
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_]{3,30}", message = "must be 3-30 letters, digits or underscores")
        String username,

        @NotBlank @Email @Size(max = 254)
        String email,

        // The 72-byte BCrypt limit is checked in RegistrationService (it counts bytes, not characters).
        @NotNull @Size(min = 10, message = "must be at least 10 characters")
        String password,

        @NotBlank
        String inviteCode) {
}
