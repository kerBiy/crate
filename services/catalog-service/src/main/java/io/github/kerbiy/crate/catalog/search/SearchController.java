package io.github.kerbiy.crate.catalog.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SearchController {

    private final SearchService search;

    SearchController(SearchService search) {
        this.search = search;
    }

    // The constraints are checked by Spring MVC's built-in method validation (400 on failure).
    @GetMapping("/albums/search")
    SearchResponse search(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return search.search(q, limit);
    }
}
