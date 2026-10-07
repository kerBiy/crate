package io.github.kerbiy.crate.review.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
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

    public static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }
}
