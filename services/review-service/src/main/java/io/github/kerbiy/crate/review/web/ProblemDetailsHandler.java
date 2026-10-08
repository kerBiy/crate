package io.github.kerbiy.crate.review.web;

import io.github.kerbiy.crate.review.catalog.AlbumNotFoundException;
import io.github.kerbiy.crate.review.catalog.CatalogUnavailableException;
import io.github.kerbiy.crate.review.users.UserServiceUnavailableException;
import io.github.kerbiy.crate.review.review.ReviewConflictException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves this service as an RFC 9457 Problem Details body (SPEC section 7).
 * The base class already does that for Spring MVC's own exceptions and ErrorResponseException;
 * the overrides below give every kind of bad input the same {@code validation-failed} shape.
 */
@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    /** Bean Validation failures on a request body: one entry per invalid field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Problems.fieldError(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, Problems.validationFailed(errors), headers, status, request);
    }

    /** Constraints on query and path parameters, e.g. {@code @Max(50) int limit}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Problems.fieldError(
                                String.valueOf(result.getMethodParameter().getParameterName()),
                                String.valueOf(error.getDefaultMessage()))))
                .toList();
        return handleExceptionInternal(ex, Problems.validationFailed(errors), headers,
                HttpStatus.BAD_REQUEST, request);
    }

    /** A value that can't be converted, e.g. {@code limit=abc} or an id that isn't a UUID. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        String expected = ex.getRequiredType() == null ? "value" : ex.getRequiredType().getSimpleName();
        ProblemDetail problem = Problems.validationFailed(List.of(
                Problems.fieldError(String.valueOf(field), "must be a valid " + expected)));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = Problems.validationFailed(List.of(
                Problems.fieldError(ex.getParameterName(), "is required")));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(AlbumNotFoundException.class)
    ProblemDetail handleAlbumNotFound(AlbumNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "album-not-found", "Album not found", "This album doesn't exist.");
    }

    /** 503, not 404: we couldn't ask, so we don't know whether the album exists. */
    @ExceptionHandler(CatalogUnavailableException.class)
    ProblemDetail handleCatalogUnavailable(CatalogUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "catalog-unavailable", "Catalog unavailable",
                "Couldn't check that this album exists. Try again in a moment.");
    }

    /** 503: without the follow list there's no feed, and an empty one would be a lie. */
    @ExceptionHandler(UserServiceUnavailableException.class)
    ProblemDetail handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "user-service-unavailable", "User service unavailable",
                "Couldn't load who you follow. Try again in a moment.");
    }

    @ExceptionHandler(ReviewConflictException.class)
    ProblemDetail handleReviewConflict(ReviewConflictException ex) {
        return problem(HttpStatus.CONFLICT, "review-conflict", "Review conflict",
                "This review kept changing while it was being saved. Try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(title);
        return problem;
    }

    /** Anything unexpected: log it, but never send internals (messages, stack traces) to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
        problem.setType(Problems.type("internal-error"));
        problem.setTitle("Internal error");
        return problem;
    }
}
