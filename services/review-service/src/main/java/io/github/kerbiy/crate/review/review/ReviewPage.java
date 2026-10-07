package io.github.kerbiy.crate.review.review;

import java.util.List;

/** One page of a list (SPEC 7). nextCursor is null on the last page. */
record ReviewPage(List<ReviewResponse> items, String nextCursor) {
}
