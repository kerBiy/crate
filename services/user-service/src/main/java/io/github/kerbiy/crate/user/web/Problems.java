package io.github.kerbiy.crate.user.web;

import java.net.URI;
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
}
