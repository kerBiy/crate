package io.github.kerbiy.crate.user.auth;

import java.time.Instant;

record TokenResponse(String accessToken, Instant expiresAt) {
}
