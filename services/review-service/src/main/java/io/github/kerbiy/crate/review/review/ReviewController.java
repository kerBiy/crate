package io.github.kerbiy.crate.review.review;

import io.github.kerbiy.crate.review.web.Problems;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ratings and reviews. Identity comes from X-User-Id, which only the gateway sets after
 * validating the JWT (SPEC 3.5). Trusting it is a known shortcut until Phase 3 (SPEC 3.6).
 */
@RestController
@RequestMapping("/reviews")
class ReviewController {

    private static final String USER_ID = "X-User-Id";

    private final ReviewService reviews;

    ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    /** Upsert: 201 when this created my review, 200 when it replaced it. Repeating it is harmless. */
    @PutMapping("/albums/{albumId}")
    ResponseEntity<ReviewResponse> upsert(@RequestHeader(name = USER_ID, required = false) UUID userId,
            @PathVariable UUID albumId, @Valid @RequestBody ReviewRequest request) {
        ReviewService.Upserted result = reviews.upsert(signedIn(userId), albumId, request.rating(),
                request.normalizedBody());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ReviewResponse.from(result.review()));
    }

    @DeleteMapping("/albums/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader(name = USER_ID, required = false) UUID userId, @PathVariable UUID albumId) {
        reviews.delete(signedIn(userId), albumId);
    }

    @GetMapping("/albums/{albumId}/me")
    ReviewResponse mine(@RequestHeader(name = USER_ID, required = false) UUID userId, @PathVariable UUID albumId) {
        return reviews.find(signedIn(userId), albumId).map(ReviewResponse::from)
                .orElseThrow(() -> Problems.exception(HttpStatus.NOT_FOUND, "review-not-found", "Review not found",
                        "You haven't rated this album."));
    }

    @GetMapping("/albums/{albumId}")
    ReviewPage ofAlbum(@PathVariable UUID albumId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return reviews.ofAlbum(albumId, parse(cursor), limit);
    }

    @GetMapping("/users/{userId}")
    ReviewPage ofUser(@PathVariable UUID userId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return reviews.ofUser(userId, parse(cursor), limit);
    }

    /** My friends' reviews, newest first. 503 when user-service can't say who I follow. */
    @GetMapping("/feed")
    ReviewPage feed(@RequestHeader(name = USER_ID, required = false) UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return reviews.feed(signedIn(userId), parse(cursor), limit);
    }

    // Not required = true: a missing header means "not signed in" (401), not a malformed request (400).
    private static UUID signedIn(UUID userId) {
        if (userId == null) {
            throw Problems.exception(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthenticated",
                    "This endpoint needs a signed-in user.");
        }
        return userId;
    }

    private static ReviewCursor parse(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            return ReviewCursor.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw Problems.invalidField("cursor", "is not a valid cursor");
        }
    }
}
