package io.github.kerbiy.crate.user.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Builds RFC 9457 problems with a stable, machine-readable {@code type}. */
public final class Problems {

    private Problems() {
    }

    public static URI type(String slug) {
        return URI.create("urn:crate:problem:" + slug);
    }

    /** An exception Spring MVC renders as the given problem (via ResponseEntityExceptionHandler). */
    public static ErrorResponseException exception(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type(slug));
        problem.setTitle(title);
        return new ErrorResponseException(status, problem, null);
    }

    /** A 400 shaped like a Bean Validation failure, so clients handle every invalid input the same way. */
    public static ProblemDetail validationFailed(List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setType(type("validation-failed"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    public static ErrorResponseException invalidField(String field, String message) {
        return new ErrorResponseException(HttpStatus.BAD_REQUEST,
                validationFailed(List.of(fieldError(field, message))), null);
    }

    /**
     * The signed-in user's id, from X-User-Id. Controllers read the header as not required, so a
     * missing one means "not signed in" (401), not a malformed request (400).
     */
    public static UUID signedIn(UUID userId) {
        if (userId == null) {
            throw exception(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthenticated",
                    "This endpoint needs a signed-in user.");
        }
        return userId;
    }

    public static ErrorResponseException userNotFound() {
        return exception(HttpStatus.NOT_FOUND, "user-not-found", "User not found", "That user doesn't exist.");
    }

    public static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }
}
