package io.github.kerbiy.crate.review.users;

/** user-service couldn't say who someone follows: an error status, a timeout, or no connection. */
public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(Throwable cause) {
        super("user-service couldn't be asked", cause);
    }
}
