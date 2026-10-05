package io.github.kerbiy.crate.user.auth;

import jakarta.validation.constraints.NotBlank;

/** {@code login} is a username or an email address. */
record LoginRequest(@NotBlank String login, @NotBlank String password) {
}
