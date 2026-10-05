package io.github.kerbiy.crate.user.auth;

import java.nio.charset.StandardCharsets;

final class Passwords {

    /** BCrypt only uses the first 72 bytes; Spring Security rejects longer input outright. */
    static final int BCRYPT_MAX_BYTES = 72;

    private Passwords() {
    }

    static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
