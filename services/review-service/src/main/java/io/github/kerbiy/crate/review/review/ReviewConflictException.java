package io.github.kerbiy.crate.review.review;

/** The review kept changing under us: still conflicting after the last retry. Rendered as 409. */
public class ReviewConflictException extends RuntimeException {

    ReviewConflictException(Throwable cause) {
        super("Review still conflicting after retries", cause);
    }
}
